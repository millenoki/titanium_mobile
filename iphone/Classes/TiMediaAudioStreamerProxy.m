/**
 * Akylas
 * Copyright (c) 2009-2010 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_MEDIA

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


@class TiMediaSoundProxy;
@implementation TiMediaAudioStreamerProxy{
@private
    double duration;
    STKAudioPlayer* player;
    NSTimer *timer;
    NSMutableArray* _playlist;
    NSInteger _playPos;
    id _currentItem;
    int _repeatMode;
    int _shuffleMode;
    ImageLoaderRequest *urlRequest;
}

#pragma mark Internal

-(void)_initWithProperties:(NSDictionary *)properties
{
    _playPos = -1;
    _repeatMode = REPEAT_NONE;
    _shuffleMode = SHUFFLE_NONE;
    [self initializeProperty:@"volume" defaultValue:@(1.0)];
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
	if (player!=nil)
	{
        STKAudioPlayerState state = [player state];
		if (state == STKAudioPlayerStatePlaying || state == STKAudioPlayerStatePaused) {
            [player stop];
            [[TiMediaAudioSession sharedSession] stopAudioSession];
		}
        [player setDelegate:nil];
        RELEASE_TO_NIL(player);
	}
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
		[player setDelegate:self];
        [player setVolume:[TiUtils doubleValue:[self valueForKey:@"volume"] def:1.0f]];
	}
	return player;
}

#pragma mark Public APIs

-(void)setPaused:(NSNumber *)paused
{
	if (player!=nil)
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
    if (player!=nil)
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

#define PLAYER_PROP_BOOL(name,func) \
-(NSNumber*)name\
{\
	if (player==nil)\
	{\
		return NUMBOOL(NO);\
	}\
	return NUMBOOL([player func]);\
}

#define PROP_BOOL(name,func) \
-(NSNumber*)name\
{\
return [self func:nil];\
}


#define PLAYER_PROP_DOUBLE(name,func) \
-(NSNumber*)name\
{\
if (player==nil)\
{\
return NUMDOUBLE(0);\
}\
return NUMDOUBLE([player func]);\
}

PLAYER_PROP_DOUBLE(progress,progress);
PLAYER_PROP_DOUBLE(state,state);

-(id)isPaused:(id)args
{
    return @([player state] == STKAudioPlayerStatePaused);
}

PROP_BOOL(paused,isPaused);

-(id)isPlaying:(id)args
{
    return @([player state] == STKAudioPlayerStatePlaying);
}
PROP_BOOL(playing,isPlaying);

-(id)isStopped:(id)args
{
    return @(player && [player state] != STKAudioPlayerStatePlaying && [player state] != STKAudioPlayerStatePaused);
}
PROP_BOOL(stopped,isStopped);


-(id)isMute:(id)args
{
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
	if (player != nil){
        return @(player.volume);
	}
	return [self valueForKey:@"volume"];
}

-(void)setVolume:(NSNumber *)newVolume
{
	if (player != nil) {
		[player setVolume:[TiUtils doubleValue:newVolume def:1.0f]];
	}
}


-(STKDataSource*)openItem:(id)item {
    NSURL* url = nil;
    NSString* filePath = nil;
    if (IS_OF_CLASS(item, TiMediaSoundProxy)) {
        url = ((TiMediaSoundProxy*)item).url;
    } else if (IS_OF_CLASS(item, TiFile)) {
        filePath = ((TiFile*)item).path;
    } else if (IS_OF_CLASS(item, NSDictionary)) {
        url = [TiUtils toURL:[item valueForKey:@"url"] proxy:self];
    } else {
        url = [TiUtils toURL:[item valueForKey:@"url"] proxy:self];
    }
    if (url && url.fileURL) {
        filePath = url.path;
        url = nil;
    }
    if (url) {
        return [[[STKAutoRecoveringHTTPDataSource alloc] initWithHTTPDataSource:[[[STKHTTPDataSource alloc] initWithURL:url] autorelease]] autorelease];
    }
    else if (filePath) {
        [[[STKLocalFileDataSource alloc] initWithFilePath:filePath] autorelease];
    }
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

-(void)setNextTrack {
    NSInteger nextPlayPos = [self getNextPosition:NO];
    if (nextPlayPos >= 0 && _playlist) {
        STKDataSource* dataSource = [self openItem:[_playlist objectAtIndex:nextPlayPos]];
        if (dataSource) {
            [player queueDataSource:dataSource withQueueItemId:@(nextPlayPos)];
        }
    }
}

-(void)openCurrentAndMaybeNext:(BOOL)openNext {
    if (!_playlist || [_playlist count] == 0) return;
    STKDataSource* dataSource = [self openItem:[_playlist objectAtIndex:_playPos]];
    if (dataSource) {
        [[self player] playDataSource:dataSource withQueueItemID:@(_playPos)];
//        if (openNext) {
//            [self setNextTrack];
//        }
    }
}

-(void)openCurrentAndNext {
    [self openCurrentAndMaybeNext:YES];
}

-(void)internalPlayOrResume {
    // indicate we're going to start playing
   
    if (player && (player.state == STKAudioPlayerStatePlaying ||
                   player.state == STKAudioPlayerStatePaused)) {
        [[self player] resume];
        return;
    }
    if (![[TiMediaAudioSession sharedSession] canPlayback]) {
        [self throwException:@"Improper audio session mode for playback"
                   subreason:[[TiMediaAudioSession sharedSession] sessionMode]
                    location:CODELOCATION];
    }
    [self openCurrentAndNext];
}

-(void)start:(id)args
{
    [self internalPlayOrResume];
}

-(void)stop:(id)args
{
	[player stop];
}

-(void)pause:(id)args
{
    [player pause];
}


-(void)playPause:(id)args
{
    if (player) {
        if (player.state == STKAudioPlayerStatePlaying) {
            [player pause];
        }
        else if (player.state == STKAudioPlayerStatePaused) {
            [[self player] resume];
        }
        else {
            [self internalPlayOrResume];
        }
    } else {
        [self internalPlayOrResume];
    }
}

-(void)next:(id)args
{
    [player playNextInQueue];
}

-(void)previous:(id)args
{
    if (player) {
        double progress = player.progress*1000;
        if (progress > 2000) {
            [player seekToTime:0];
            [player resume];
            return;
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
    if (player) {
        int time = [TiUtils intValue:args]/1000;
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

-(void)updateCurrentItem:(NSInteger)playPos {
    _playPos = playPos;
    if (_playPos != -1) {
        duration = (int)player.duration*1000;
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

MAKE_SYSTEM_PROP(STATE_INITIALIZED,STKAudioPlayerStateReady);
MAKE_SYSTEM_PROP(STATE_ERROR,STKAudioPlayerStateError);
MAKE_SYSTEM_PROP(STATE_PLAYING,STKAudioPlayerStatePlaying);
MAKE_SYSTEM_PROP(STATE_BUFFERING,STKAudioPlayerStateBuffering);
MAKE_SYSTEM_PROP(STATE_STOPPED,STKAudioPlayerStateStopped);
MAKE_SYSTEM_PROP(STATE_PAUSED,STKAudioPlayerStatePaused);


-(NSString*)stateToString:(int)state
{
	switch(state)
	{
		case STKAudioPlayerStateReady:
			return @"initialized";
		case STKAudioPlayerStateError:
			return @"error";
		case STKAudioPlayerStatePlaying:
			return @"playing";
		case STKAudioPlayerStateBuffering:
			return @"buffering";
		case STKAudioPlayerStateStopped:
			return @"stopped";
		case STKAudioPlayerStatePaused:
			return @"paused";
	}
	return @"unknown";
}

-(NSString*)stateDescription:(id)arg
{
	ENSURE_SINGLE_ARG(arg,NSNumber);
	return [self stateToString:[TiUtils intValue:arg]];
}

#pragma mark Delegates

- (void)updateProgress:(NSTimer *)updatedTimer
{
    if (duration == 0) {
        duration = (int)player.duration*1000;
    }
    if ([self _hasListeners:@"progress"])
    {
        NSDictionary *event = @{
                            @"progress":@(round(player.progress*1000)),
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
    if ([self _hasListeners:@"state"])
    {
        NSDictionary *event = [NSDictionary dictionaryWithObjectsAndKeys:[self state],@"state",[self stateToString:player.state],@"description",nil];
        [self fireEvent:@"state" withObject:event checkForListener:NO];
    }
    if (state == STKAudioPlayerStatePlaying) {
        
        [[TiMediaAudioSession sharedSession] startAudioSession];
        
        if (timer) {
            [timer invalidate];
            timer = nil;
        }
        timer = [[NSTimer scheduledTimerWithTimeInterval:1 target:self selector:@selector(updateProgress:) userInfo:nil repeats:YES] retain];
        [timer fire];
    } else {
        if (timer) {
            [timer invalidate];
            timer = nil;
        }
        if (state == STKAudioPlayerStateBuffering) {
            if ([self _hasListeners:@"buffering"])
            {
                NSDictionary *event = [NSDictionary dictionaryWithObjectsAndKeys:@(0),@"progress",nil];
                [self fireEvent:@"buffering" withObject:event checkForListener:NO];
            }
        }
        if (state == STKAudioPlayerStateStopped || state == STKAudioPlayerStateError) {
            
            [[TiMediaAudioSession sharedSession] stopAudioSession];
            
        }
    }
}

/// Raised when an item has finished playing
-(void) audioPlayer:(STKAudioPlayer*)audioPlayer didFinishPlayingQueueItemId:(NSObject*)queueItemId withReason:(STKAudioPlayerStopReason)stopReason andProgress:(double)progress andDuration:(double)duration {
    
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
    if (!player || player.state == STKAudioPlayerStateStopped || player.state == STKAudioPlayerStateError) {
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

@end

#endif