/**
 * Akylas
 * Copyright (c) 2009-2010 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_MEDIA

#import <MediaPlayer/MediaPlayer.h>
#import <MediaPlayer/MPNowPlayingInfoCenter.h>
#import <MediaPlayer/MPMediaItem.h>

#import "TiMediaAudioStreamerProxy.h"
#import "TiUtils.h"
#import "TiMediaAudioSession.h"
#import "TiMediaSoundProxy.h"
#import "TiFile.h"
#import "STKHTTPDataSource.h"
#import "STKAutoRecoveringHTTPDataSource.h"
#import "STKLocalFileDataSource.h"
#import "TiApp.h"

@interface STKM3u8DataSource : STKDataSource
@property (nonatomic, retain) NSURL* url;
-(id) initWithURL:(NSURL*)url;
@end

@implementation STKM3u8DataSource

-(id) initWithURL:(NSURL *)urlIn
{
    if (self = [super init])
    {
        self.url = urlIn;
    }
    return self;
}
@end

typedef enum {
    REPEAT_NONE,
    REPEAT_CURRENT,
    REPEAT_ALL
} RepeatMode;

typedef enum {
    SHUFFLE_NONE,
    SHUFFLE_NORMAL,
    SHUFFLE_AUTO
} ShuffleMode;

typedef enum {
    STATE_INITIALIZED,
    STATE_ERROR,
    STATE_PLAYING,
    STATE_BUFFERING,
    STATE_STOPPED,
    STATE_PAUSED
} PlayState;


@class TiMediaSoundProxy;
@implementation TiMediaAudioStreamerProxy{
@private
    double duration;
    STKAudioPlayer* player;
    NSTimer *timer;
    NSMutableArray* _playlist;
    NSInteger _playPos;
    int _state;
    id _currentItem;
    int _repeatMode;
    int _shuffleMode;
    ImageLoaderRequest *urlRequest;
    MPMoviePlayerController *moviePlayer;
    float volume;
}

#pragma mark Internal

-(void)_initWithProperties:(NSDictionary *)properties
{
    _playPos = -1;
    volume = 1.0f;
    _state = STATE_STOPPED;
    _repeatMode = REPEAT_NONE;
    _shuffleMode = SHUFFLE_NONE;
    [self initializeProperty:@"volume" defaultValue:@(volume)];
    dispatch_async(dispatch_get_main_queue(), ^{
        [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(remoteControlEvent:) name:kTiRemoteControlNotification object:nil];
    });
}

-(void)_destroy
{
    dispatch_sync(dispatch_get_main_queue(), ^{
        [[NSNotificationCenter defaultCenter] removeObserver:self];
    });
	if (timer!=nil)
	{
		[timer invalidate];
		RELEASE_TO_NIL(timer);
	}
    if ([[self playing] boolValue]) {
        [self stop:nil];
        [[TiMediaAudioSession sharedSession] stopAudioSession];
    }
    [player setDelegate:nil];
    RELEASE_TO_NIL(player);
    [self cleanMoviePlayer];
    [super _destroy];
}

-(NSString*)apiName
{
    return @"Ti.Media.AudioStreamer";
}


-(STKAudioPlayer*)player
{
	if (player==nil)
	{
		player = [[STKAudioPlayer alloc] initWithOptions:(STKAudioPlayerOptions){
            .flushQueueOnSeek = YES,
            .enableVolumeMixer = YES,
        }];
        [player setVolume:volume];
		[player setDelegate:self];
        [player setVolume:[TiUtils doubleValue:[self valueForKey:@"volume"] def:1.0f]];
	}
	return player;
}

-(void)cleanMoviePlayer {
    if (moviePlayer != nil)
    {
        NSNotificationCenter* center = [NSNotificationCenter defaultCenter];
        [center removeObserver:self name:MPMoviePlayerLoadStateDidChangeNotification object:nil];
        [center removeObserver:self name:MPMoviePlayerPlaybackDidFinishNotification object:nil];
        [center removeObserver:self name:MPMoviePlayerNowPlayingMovieDidChangeNotification object:nil];
        [center removeObserver:self name:MPMovieDurationAvailableNotification object:nil];
        [center removeObserver:self name:MPMoviePlayerTimedMetadataUpdatedNotification object:nil];
        [moviePlayer.view removeFromSuperview];
        [moviePlayer stop];
        [self updateState:[self stateFromMPState:moviePlayer.playbackState]];
        RELEASE_TO_NIL(moviePlayer)
    }
}

-(MPMoviePlayerController*)moviePlayerWithUrl:(NSURL*)url
{
    if (moviePlayer==nil)
    {
        if (player) {
            [player stop];
        }
        moviePlayer = [[MPMoviePlayerController alloc] initWithContentURL:url];
        moviePlayer.allowsAirPlay = YES;
        moviePlayer.shouldAutoplay = YES;
//        moviePlayer.fullscreen = YES;
        moviePlayer.view.hidden = YES;
        [moviePlayer setMovieSourceType:MPMovieSourceTypeStreaming];
        [[TiApp app].window addSubview: moviePlayer.view];
        [[MPMusicPlayerController applicationMusicPlayer] setVolume:volume];
        NSNotificationCenter* center = [NSNotificationCenter defaultCenter];
        [center addObserver:self selector:@selector(moviePlaybackStateChanged:) name:MPMoviePlayerLoadStateDidChangeNotification object:nil];
        [center addObserver:self selector:@selector(moviePlayBackDidFinish:) name:MPMoviePlayerPlaybackDidFinishNotification object:nil];
        [center addObserver:self selector:@selector(moviePlayBackDidStart:) name:MPMoviePlayerNowPlayingMovieDidChangeNotification object:nil];
        [center addObserver:self selector:@selector(moviePlayBackDurationAvailable:) name:MPMovieDurationAvailableNotification object:nil];
        [center addObserver:self selector:@selector(movieMetadataUpdate:) name:MPMoviePlayerTimedMetadataUpdatedNotification object:nil];
    }
    return moviePlayer;
}

#pragma mark Public APIs

-(void)setPaused:(NSNumber *)paused
{
    if (moviePlayer) {
        [moviePlayer pause];
    }
	else if (player)
	{
		if ([TiUtils boolValue:paused])
		{
			[player pause];
		}
		else
		{
			[player resume];
		}
	}
}


-(void)setMute:(NSNumber *)mute
{
    if (moviePlayer) {
        [[MPMusicPlayerController applicationMusicPlayer] setVolume:[TiUtils boolValue:mute]?0:volume];
    }
    else if (player)
    {
        if ([TiUtils boolValue:mute])
        {
            [player mute];
        }
        else
        {
            [player unmute];
        }
    }
}

#define PROP_BOOL(name,func) \
-(NSNumber*)name\
{\
return [self func:nil];\
}

-(id)progress
{
    if (moviePlayer) {
        int current = moviePlayer.currentPlaybackTime;
        return @(current*1000);
    }
    else if (player)
    {
        return @(round([player progress]*1000));
    }
    return @(0);
}
-(id)state
{
//    if (moviePlayer) {
//        return @(moviePlayer.playbackState);
//    }
//    else if (player)
//    {
//        return @([player state]);
//    }
    return @(_state);
}

-(id)isPaused:(id)args
{
    return @(_state == STATE_PAUSED);
//    if (moviePlayer) {
//        return @(moviePlayer.playbackState == MPMoviePlaybackStatePaused);
//    }
//    return @([player state] == STKAudioPlayerStatePaused);
}

PROP_BOOL(paused,isPaused);

-(id)isPlaying:(id)args
{
    return @(_state == STATE_PLAYING);
//    if (moviePlayer) {
//        return @(moviePlayer.playbackState == MPMoviePlaybackStatePlaying);
//    }
//    return @([player state] == STKAudioPlayerStatePlaying);
}
PROP_BOOL(playing,isPlaying);

-(id)isStopped:(id)args
{
    return @(_state == STATE_STOPPED || _state == STATE_ERROR);
//    if (moviePlayer) {
//        return @(moviePlayer.playbackState != MPMoviePlaybackStatePlaying && moviePlayer.playbackState != MPMoviePlaybackStatePaused);
//    }
//    return @(player && [player state] != STKAudioPlayerStatePlaying && [player state] != STKAudioPlayerStatePaused);
}
PROP_BOOL(stopped,isStopped);


-(id)isMute:(id)args
{
    if (moviePlayer) {
        return @([MPMusicPlayerController applicationMusicPlayer].volume == 0);
    }
    return @(player && [player muted]);
}
PROP_BOOL(muted,isMute);


-(id)currentItem
{
    return _currentItem;
}

-(NSNumber *)duration
{
	return @(duration);
}


-(id)index
{
    if (_currentItem) {
        return @(_playPos);
    }
    return @(-1);
}


-(NSNumber *)volume
{
	return @(volume);
}

-(void)setVolume:(NSNumber *)newVolume
{
    volume = [TiUtils doubleValue:newVolume def:1.0f];
    if (moviePlayer) {
        [[MPMusicPlayerController applicationMusicPlayer] setVolume:volume];
    }
	if (player != nil) {
		[player setVolume:volume];
	}
}


-(STKDataSource*)openItem:(id)item {
    NSURL* url = nil;
    if (IS_OF_CLASS(item, TiMediaSoundProxy)) {
        url = ((TiMediaSoundProxy*)item).url;
    } else if (IS_OF_CLASS(item, TiFile)) {
        url = [TiUtils toURL:((TiFile*)item).path proxy:self];
    } else if (IS_OF_CLASS(item, NSDictionary)) {
        url = [TiUtils toURL:[item valueForKey:@"url"] proxy:self];
    } else {
        url = [TiUtils toURL:[TiUtils stringValue:item] proxy:self];
    }
    if ([[url pathExtension] containsString:@"m3u"]) {
        return [[[STKM3u8DataSource alloc] initWithURL:url] autorelease];
    }
    if (url)
        return [STKAudioPlayer dataSourceFromURL:url];
    return nil;
}

-(void)setPlaylist:(id)args
{
    [self stop:nil];
	RELEASE_TO_NIL(_playlist);
    
    if (IS_OF_CLASS(args, NSArray)) {
        _playlist = [[NSMutableArray arrayWithArray:args] retain];
    }
    else {
        _playlist = [[NSMutableArray arrayWithObject:args] retain];
    }
    _playPos = 0;
}


-(void)play:(id)args
{
    [self internalPlayOrResume];
}

-(NSInteger) getNextPosition:(BOOL)force {
    if (!force && _repeatMode == REPEAT_CURRENT) {
        if (_playPos < 0) {
            return 0;
        }
        return _playPos+1;
    } else if (_shuffleMode == SHUFFLE_NORMAL) {
//        if (_playPos >= 0) {
//            mHistory.add(_playPos);
//        }
//        if (mHistory.size() > MAX_HISTORY_SIZE) {
//            mHistory.remove(0);
//        }
//        final int numTracks = mPlayListLen;
//        final int[] tracks = new int[numTracks];
//        for (int i = 0; i < numTracks; i++) {
//            tracks[i] = i;
//        }
//        
//        final int numHistory = mHistory.size();
//        int numUnplayed = numTracks;
//        for (int i = 0; i < numHistory; i++) {
//            final int idx = mHistory.get(i).intValue();
//            if (idx < numTracks && tracks[idx] >= 0) {
//                numUnplayed--;
//                tracks[idx] = -1;
//            }
//        }
//        if (numUnplayed <= 0) {
//            if (mRepeatMode == REPEAT_ALL || force) {
//                numUnplayed = numTracks;
//                for (int i = 0; i < numTracks; i++) {
//                    tracks[i] = i;
//                }
//            } else {
//                return -1;
//            }
//        }
//        int skip = 0;
//        if (_shuffleMode == SHUFFLE_NORMAL || _shuffleMode == SHUFFLE_AUTO) {
//            skip = mShuffler.nextInt(numUnplayed);
//        }
//        int cnt = -1;
//        while (true) {
//            while (tracks[++cnt] < 0) {
//                ;
//            }
//            skip--;
//            if (skip < 0) {
//                break;
//            }
//        }
//        return cnt;
        return _playPos + 1;
    } else if (_shuffleMode == SHUFFLE_AUTO) {
//        doAutoShuffleUpdate();
        return _playPos + 1;
    } else {
        if (_playPos >= [_playlist count] - 1) {
            if (_repeatMode == REPEAT_NONE && !force) {
                return -1;
            } else if (_repeatMode == REPEAT_ALL || force) {
                return 0;
            }
            return -1;
        } else {
            return _playPos + 1;
        }
    }
}

-(NSInteger) getPreviousPosition:(BOOL)force {
    if (!force && _repeatMode == REPEAT_CURRENT) {
        return MAX(_playPos - 1, 0);
    } else if (_shuffleMode == SHUFFLE_NORMAL) {
        //        if (_playPos >= 0) {
        //            mHistory.add(_playPos);
        //        }
        //        if (mHistory.size() > MAX_HISTORY_SIZE) {
        //            mHistory.remove(0);
        //        }
        //        final int numTracks = mPlayListLen;
        //        final int[] tracks = new int[numTracks];
        //        for (int i = 0; i < numTracks; i++) {
        //            tracks[i] = i;
        //        }
        //
        //        final int numHistory = mHistory.size();
        //        int numUnplayed = numTracks;
        //        for (int i = 0; i < numHistory; i++) {
        //            final int idx = mHistory.get(i).intValue();
        //            if (idx < numTracks && tracks[idx] >= 0) {
        //                numUnplayed--;
        //                tracks[idx] = -1;
        //            }
        //        }
        //        if (numUnplayed <= 0) {
        //            if (mRepeatMode == REPEAT_ALL || force) {
        //                numUnplayed = numTracks;
        //                for (int i = 0; i < numTracks; i++) {
        //                    tracks[i] = i;
        //                }
        //            } else {
        //                return -1;
        //            }
        //        }
        //        int skip = 0;
        //        if (_shuffleMode == SHUFFLE_NORMAL || _shuffleMode == SHUFFLE_AUTO) {
        //            skip = mShuffler.nextInt(numUnplayed);
        //        }
        //        int cnt = -1;
        //        while (true) {
        //            while (tracks[++cnt] < 0) {
        //                ;
        //            }
        //            skip--;
        //            if (skip < 0) {
        //                break;
        //            }
        //        }
        //        return cnt;
        return MAX(_playPos - 1, 0);
    } else if (_shuffleMode == SHUFFLE_AUTO) {
        //        doAutoShuffleUpdate();
        return MAX(_playPos - 1, 0);
    } else {
        if (_playPos >= [_playlist count] - 1) {
//            if (_repeatMode == REPEAT_NONE && !force) {
//                return -1;
//            } else if (_repeatMode == REPEAT_ALL || force) {
//                return [_playlist count] - 1;
//            }
            return MAX(_playPos - 1, 0);
        } else {
            return MAX(_playPos - 1, 0);
        }
    }
}

-(void)playNextTrack {
    NSInteger nextPlayPos = [self getNextPosition:NO];
    if (nextPlayPos >= 0 && _playlist) {
        _playPos = nextPlayPos;
        [self openCurrentAndNext];
    }
}

-(void)setNextTrack {
    if (moviePlayer) return;
    NSInteger nextPlayPos = [self getNextPosition:NO];
    if (nextPlayPos >= 0 && _playlist) {
        STKDataSource* dataSource = [self openItem:[_playlist objectAtIndex:nextPlayPos]];
        if (dataSource) {
            if (IS_OF_CLASS(dataSource, STKM3u8DataSource)) {
                
            }
            else {
                [[self player] queueDataSource:dataSource withQueueItemId:@(nextPlayPos)];
            }
        }
    }
}

-(void)openCurrentAndMaybeNext:(BOOL)openNext {
    if (!_playlist || [_playlist count] == 0) return;
    [self cleanMoviePlayer];
    STKDataSource* dataSource = [self openItem:[_playlist objectAtIndex:_playPos]];
    if (dataSource) {
        if (IS_OF_CLASS(dataSource, STKM3u8DataSource)) {
            [[self moviePlayerWithUrl:((STKM3u8DataSource*)dataSource).url] play];
        }
        else {
            [[self player] playDataSource:dataSource withQueueItemID:@(_playPos)];
        }

    }
}

-(void)openCurrentAndNext {
    [self openCurrentAndMaybeNext:YES];
}

-(void)internalPlayOrResume {
    // indicate we're going to start playing
    if (![[self stopped] boolValue])
    {
        if (moviePlayer) {
            [moviePlayer play];
            [self updateState:[self stateFromMPState:moviePlayer.playbackState]];
        } else {
            [[self player] resume];
        }
    } else {
        if (![[TiMediaAudioSession sharedSession] canPlayback]) {
            [self throwException:@"Improper audio session mode for playback"
                       subreason:[[TiMediaAudioSession sharedSession] sessionMode]
                        location:CODELOCATION];
        }
        [self openCurrentAndNext];
    }
    
}

-(void)start:(id)args
{
    [self internalPlayOrResume];
}

-(void)stop:(id)args
{
    if (moviePlayer) {
        [moviePlayer stop];
        [self updateState:[self stateFromMPState:moviePlayer.playbackState]];
    } else {
        [player stop];
    }
}

-(void)pause:(id)args
{
    if (moviePlayer) {
        [moviePlayer pause];
        [self updateState:[self stateFromMPState:moviePlayer.playbackState]];
    } else {
        [player pause];
    }
}


-(void)playPause:(id)args
{
    if ([[self playing] boolValue]) {
        [self pause:nil];
    } else {
        [self play:nil];
    }
}

-(void)next:(id)args
{
    if (moviePlayer) {
        [self playNextTrack];
    }
    else {
        [player playNextInQueue];
    }
}

-(void)previous:(id)args
{
    if ([[self playing] boolValue]) {
        double progress = [[self progress] doubleValue];
        if (progress > 2000) {
            if (moviePlayer) {
                [moviePlayer setCurrentPlaybackTime:0];
                [moviePlayer play];
                [self updateState:[self stateFromMPState:moviePlayer.playbackState]];
                return;
            }
            else if (player) {
                [player seekToTime:0];
                [player resume];
                return;
            }
        }
    }
    NSInteger prevPlayPos = [self getPreviousPosition:NO];
    if (prevPlayPos >= 0) {
        _playPos = prevPlayPos;
        [self openCurrentAndNext];
    }
}

-(void)seek:(id)args {
    ENSURE_SINGLE_ARG_OR_NIL(args, NSNumber)
    double time = [TiUtils doubleValue:args]/1000;
    if (moviePlayer) {
        [moviePlayer setCurrentPlaybackTime:time];
        return;
    }
    else if (player) {
        [player seekToTime:time];
    }
    
}

NSDictionary* metadataKeys;

-(NSDictionary*)metadataKeys
{
    if (metadataKeys == nil) {
        metadataKeys = [@{
                         @"title":MPMediaItemPropertyTitle,
                         @"artist":MPMediaItemPropertyArtist,
                         @"album":MPMediaItemPropertyAlbumTitle,
                         @"duration":MPMediaItemPropertyPlaybackDuration,
                         @"tracknumber":MPMediaItemPropertyAlbumTrackNumber,
                         @"date":MPMediaItemPropertyReleaseDate,
                         @"year":MPMediaItemPropertyReleaseDate,
                         @"composer":MPMediaItemPropertyComposer,
                         @"comment":MPMediaItemPropertyComments,
                         @"genre":MPMediaItemPropertyGenre,
                         @"compilation":MPMediaItemPropertyIsCompilation
                         } retain];
    }
    return metadataKeys;
}


-(void)updateMetadataAlbumArt:(UIImage*)image {
    NSMutableDictionary* mediaInfo  = [[NSMutableDictionary alloc] initWithDictionary:[[MPNowPlayingInfoCenter defaultCenter] nowPlayingInfo]];
    MPMediaItemArtwork *albumArt = [[MPMediaItemArtwork alloc] initWithImage: image];
    [mediaInfo setObject:albumArt forKey:MPMediaItemPropertyArtwork];
    [[MPNowPlayingInfoCenter defaultCenter] setNowPlayingInfo:mediaInfo];
    [mediaInfo release];
}

-(id)sanitizeURL:(id)value
{
    if (value == [NSNull null])
    {
        return nil;
    }
    
    if([value isKindOfClass:[NSString class]])
    {
        NSURL * result = [TiUtils toURL:value proxy:self];
        if (result != nil)
        {
            return result;
        }
    }
    
    return value;
}

-(void)startImageLoad:(NSURL *)url;
{
    [self cancelPendingImageLoads]; //Just in case we have a crusty old urlRequest.
    NSDictionary* info = nil;
    NSNumber* hires = [self valueForKey:@"hires"];
    if (hires) {
        info = [NSDictionary dictionaryWithObject:hires forKey:@"hires"];
    }
    urlRequest = [[[ImageLoader sharedLoader] loadImage:url delegate:self options:[self valueForUndefinedKey:@"httpOptions"] userInfo:info] retain];
}

-(void)cancelPendingImageLoads
{
    // cancel a pending request if we have one pending
    if (urlRequest!=nil)
    {
        [urlRequest cancel];
        RELEASE_TO_NIL(urlRequest);
    }
}

-(void)imageLoadSuccess:(ImageLoaderRequest*)request image:(id)image
{
    if (request != urlRequest || !image)
    {
        return;
    }
    [self updateMetadataAlbumArt:image];
    RELEASE_TO_NIL(urlRequest);
}

-(void)imageLoadFailed:(ImageLoaderRequest*)request error:(NSError*)error
{
    if (request == urlRequest)
    {
        RELEASE_TO_NIL(urlRequest);
    }
}

-(void)imageLoadCancelled:(ImageLoaderRequest *)request
{
}

-(UIImage*)downloadAlbumArt:(id)obj {
    if (!obj) return;
    if (IS_OF_CLASS(obj, UIImage)) {
        return obj;
    }
    if (IS_OF_CLASS(obj, TiBlob)) {
        return [(TiBlob*)obj image];
    }
    
    NSURL* imageURL = [self sanitizeURL:obj];
    if (![imageURL isKindOfClass:[NSURL class]]) {
        NSLog(@"[ERROR] invalid image type: \"%@\" is not a TiBlob, URL, TiFile",imageURL);
        return nil;
    }
    NSURL *url = [TiUtils toURL:[imageURL absoluteString] proxy:self];
    urlRequest = [[[ImageLoader sharedLoader] loadImage:url delegate:self options:[self valueForUndefinedKey:@"httpOptions"] userInfo:@{@"hires":@(YES)}] retain];
    return nil;
}

-(void)updateMetadata:(NSDictionary*)data {
    NSMutableDictionary* mediaInfo  = [[NSMutableDictionary alloc] init];
    NSDictionary* metadataKeys = [self metadataKeys];
    [data enumerateKeysAndObjectsUsingBlock:^(id key, id obj, BOOL *stop) {
        NSString* realKey = [metadataKeys objectForKey:key];
        if (realKey) {
            [mediaInfo setObject:obj forKey:realKey];
        }
    }];
    if (duration > 0) {
        [mediaInfo setObject:@(duration) forKey:MPMediaItemPropertyPlaybackDuration];
    }
    UIImage* covertart = [self downloadAlbumArt:[data objectForKey:@"artwork"]];
    if (covertart) {
        MPMediaItemArtwork *albumArt = [[MPMediaItemArtwork alloc] initWithImage: covertart];
        [mediaInfo setObject:albumArt forKey:MPMediaItemPropertyArtwork];
    }
    [[MPNowPlayingInfoCenter defaultCenter] setNowPlayingInfo:mediaInfo];
    [mediaInfo release];
}

-(void)updateMetadata {
    if (IS_OF_CLASS(_currentItem, NSDictionary)) {
        [self updateMetadata:_currentItem];
    }
    else {
        [self updateMetadata:[self valueForKey:@"metadata"]];
    }
}

-(void)updateDuration {
    if (duration != 0) {
        return;
    }
    if (moviePlayer) {
        duration =  moviePlayer.duration*1000;
    } else if (player) {
        duration = (int)player.duration*1000;
    }
    else {
        duration = 0;
        
    }
}

-(void)updateCurrentItem:(NSInteger)playPos {
    _playPos = playPos;
    if (_playPos != -1) {
        [self updateDuration];
        _currentItem = [_playlist objectAtIndex:_playPos];
        [self updateMetadata];
        if ([self _hasListeners:@"change"])
        {
            NSDictionary *event = @{
                                    @"track":_currentItem,
                                    @"duration":@(duration),
                                    @"index":@(_playPos)
                                    };
            [self fireEvent:@"change" withObject:event checkForListener:NO];
        }
        [self setNextTrack];
    }
}

MAKE_SYSTEM_PROP(STATE_INITIALIZED,STATE_INITIALIZED);
MAKE_SYSTEM_PROP(STATE_ERROR,STATE_ERROR);
MAKE_SYSTEM_PROP(STATE_PLAYING,STATE_PLAYING);
MAKE_SYSTEM_PROP(STATE_BUFFERING,STATE_BUFFERING);
MAKE_SYSTEM_PROP(STATE_STOPPED,STATE_STOPPED);
MAKE_SYSTEM_PROP(STATE_PAUSED,STATE_PAUSED);


-(int)stateFromSTKState:(STKAudioPlayerState)state {
    switch(state)
    {
        case STKAudioPlayerStateReady:
            return STATE_INITIALIZED;
        case STKAudioPlayerStateError:
            return STATE_ERROR;
        case STKAudioPlayerStatePlaying:
        case STKAudioPlayerStateRunning:
            return STATE_PLAYING;
        case STKAudioPlayerStateBuffering:
            return STATE_BUFFERING;
        case STKAudioPlayerStatePaused:
            return STATE_PAUSED;
        case STKAudioPlayerStateStopped:
        case STKAudioPlayerStateDisposed:
            return STATE_STOPPED;
    }
}

-(int)stateFromMPState:(MPMoviePlaybackState)state {
    switch(state)
    {
        case MPMoviePlaybackStateSeekingBackward:
        case MPMoviePlaybackStateSeekingForward:
        case MPMoviePlaybackStatePlaying:
            return STATE_PLAYING;
        case MPMoviePlaybackStatePaused:
        case MPMoviePlaybackStateInterrupted:
            return STATE_PAUSED;
        case MPMoviePlaybackStateStopped:
            return STATE_STOPPED;
    }
}

-(int)stateFromMPLoadState:(MPMovieLoadState)state {
    switch(state)
    {
        case MPMovieLoadStatePlayable:
        case MPMovieLoadStatePlaythroughOK:
            return STATE_INITIALIZED;
        case MPMovieLoadStateUnknown:
            return STATE_ERROR;
        case MPMovieLoadStateStalled:
            return STATE_PAUSED;
    }
}

-(void)updateState:(int)newState {
    if (_state != newState) {
        _state = newState;
        if (_state == STATE_PLAYING) {
            
            [[TiMediaAudioSession sharedSession] startAudioSession];
            
            if (!timer) {
                dispatch_async(dispatch_get_main_queue(), ^{
                    timer = [[NSTimer scheduledTimerWithTimeInterval:1 target:self selector:@selector(updateProgress:) userInfo:nil repeats:YES] retain];
                    [timer fire];
                });
            }
        } else {
            if (timer) {
                dispatch_async(dispatch_get_main_queue(), ^{
                    [timer invalidate];
                    RELEASE_TO_NIL(timer)
                });
            }
            if (_state == STATE_BUFFERING) {
                if ([self _hasListeners:@"buffering"])
                {
                    NSDictionary *event = [NSDictionary dictionaryWithObjectsAndKeys:@(0),@"progress",nil];
                    [self fireEvent:@"buffering" withObject:event checkForListener:NO];
                }
            }
            if (_state == STATE_STOPPED || _state == STATE_ERROR) {
                
                [[TiMediaAudioSession sharedSession] stopAudioSession];
                
            }
        }
        if ([self _hasListeners:@"state"])
        {
            NSDictionary *event = [NSDictionary dictionaryWithObjectsAndKeys:@(_state),@"state",[self stateToString:_state],@"description",nil];
            [self fireEvent:@"state" withObject:event checkForListener:NO];
        }
    }
}

-(NSString*)stateToString:(int)state
{
	switch(state)
	{
		case STATE_INITIALIZED:
			return @"initialized";
		case STATE_ERROR:
			return @"error";
		case STATE_PLAYING:
			return @"playing";
		case STATE_BUFFERING:
			return @"buffering";
        case STATE_PAUSED:
            return @"paused";
		case STATE_STOPPED:
			return @"stopped";
	}
}

-(NSString*)stateDescription:(id)arg
{
	ENSURE_SINGLE_ARG(arg,NSNumber);
	return [self stateToString:[TiUtils intValue:arg]];
}

#pragma mark Delegates

- (void)updateProgress:(NSTimer *)updatedTimer
{
    [self updateDuration];
    if ([self _hasListeners:@"progress"])
    {
        NSDictionary *event = @{
                            @"progress":[self progress],
                            @"duration":@(duration)
                            };
        [self fireEvent:@"progress" withObject:event checkForListener:NO];
    }
}

/// Raised when an item has started playing
-(void) audioPlayer:(STKAudioPlayer*)audioPlayer didStartPlayingQueueItemId:(NSObject*)queueItemId {
    [self updateCurrentItem:[(NSNumber*)queueItemId integerValue]];
}
/// Raised when an item has finished buffering (may or may not be the currently playing item)
/// This event may be raised multiple times for the same item if seek is invoked on the player
-(void) audioPlayer:(STKAudioPlayer*)audioPlayer didFinishBufferingSourceWithQueueItemId:(NSObject*)queueItemId {
    
}
/// Raised when the state of the player has changed
-(void) audioPlayer:(STKAudioPlayer*)audioPlayer stateChanged:(STKAudioPlayerState)state previousState:(STKAudioPlayerState)previousState {
    [self updateState:[self stateFromSTKState:state]];
    
}

/// Raised when an item has finished playing
-(void) audioPlayer:(STKAudioPlayer*)audioPlayer didFinishPlayingQueueItemId:(NSObject*)queueItemId withReason:(STKAudioPlayerStopReason)stopReason andProgress:(double)progress andDuration:(double)dur {
    duration = 0;
}
/// Raised when an unexpected and possibly unrecoverable error has occured (usually best to recreate the STKAudioPlauyer)
-(void) audioPlayer:(STKAudioPlayer*)audioPlayer unexpectedError:(STKAudioPlayerErrorCode)errorCode {
    
}

/// Optionally implemented to get logging information from the STKAudioPlayer (used internally for debugging)
-(void) audioPlayer:(STKAudioPlayer*)audioPlayer logInfo:(NSString*)line {
    
}
/// Raised when items queued items are cleared (usually because of a call to play, setDataSource or stop)
-(void) audioPlayer:(STKAudioPlayer*)audioPlayer didCancelQueuedItems:(NSArray*)queuedItems {
    
}
- (void)remoteControlEvent:(NSNotification*)note
{
    if ((!player && !moviePlayer) || _state == STATE_STOPPED || _state == STATE_ERROR) {
        return;
    }
    UIEvent *receivedEvent = [[note userInfo]objectForKey:@"event"];
    if (receivedEvent.type == UIEventTypeRemoteControl) {
        
        switch (receivedEvent.subtype) {
                
            case UIEventSubtypeRemoteControlTogglePlayPause:
                [self playPause: nil];
                break;
            case UIEventSubtypeRemoteControlPlay:
                [self play: nil];
                break;
            case UIEventSubtypeRemoteControlPause:
                [self pause: nil];
                break;
                
            case UIEventSubtypeRemoteControlPreviousTrack:
                [self previous: nil];
                break;
                
            case UIEventSubtypeRemoteControlNextTrack:
                [self next: nil];
                break;
                
            default:
                break;
        }
    }
}
- (void) moviePlaybackStateChanged:(NSNotification*)notification {
    MPMovieLoadState loadState = moviePlayer.loadState;
    MPMoviePlaybackState playbackState = moviePlayer.playbackState;
//   if (loadState == MPMovieLoadStatePlayable) {
        [self updateState:[self stateFromMPState:playbackState]];
//    }
//    else {
//        [self updateState:[self stateFromMPLoadState:loadState]];
//    }
}
- (void) moviePlayBackDidFinish:(NSNotification*)notification {
    duration = 0;
    [self playNextTrack];
}
- (void) moviePlayBackDidStart:(NSNotification*)notification {
    [self updateCurrentItem:_playPos];
    [self updateState:[self stateFromMPState:moviePlayer.playbackState]];
}
- (void) moviePlayBackDurationAvailable:(NSNotification*)notification {
    [self updateDuration];
}
- (void) movieMetadataUpdate:(NSNotification*)notification {
    if ([moviePlayer timedMetadata]!=nil && [[moviePlayer timedMetadata] count] > 0) {
        MPTimedMetadata *firstMeta = [[moviePlayer timedMetadata] objectAtIndex:0];
        NSDictionary* metadataInfo = firstMeta.allMetadata;
    }
}



@end

#endif