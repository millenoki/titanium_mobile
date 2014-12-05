/**
 * Akylas
 * Copyright (c) 2009-2010 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_MEDIA

//#import <MediaPlayer/MediaPlayer.h>
#import <MediaPlayer/MPNowPlayingInfoCenter.h>
//#import <MediaPlayer/MPMediaItem.h>
#import <AVFoundation/AVFoundation.h>
#import <MediaPlayer/MPMusicPlayerController.h>

#import "TiMediaAudioStreamerProxy.h"
#import "TiUtils.h"
#import "TiMediaAudioSession.h"
#import "TiMediaSoundProxy.h"
#import "TiFile.h"
//#import "STKHTTPDataSource.h"
//#import "STKAutoRecoveringHTTPDataSource.h"
//#import "STKLocalFileDataSource.h"
#import "TiApp.h"

static void *MyStreamingMovieViewControllerTimedMetadataObserverContext = &MyStreamingMovieViewControllerTimedMetadataObserverContext;
static void *MyStreamingMovieViewControllerRateObservationContext = &MyStreamingMovieViewControllerRateObservationContext;
static void *MyStreamingMovieViewControllerCurrentItemObservationContext = &MyStreamingMovieViewControllerCurrentItemObservationContext;
static void *MyStreamingMovieViewControllerPlayerItemStatusObserverContext = &MyStreamingMovieViewControllerPlayerItemStatusObserverContext;

static void *MyStreamingMovieViewControllerPlayerItemLoadingObserverContext = &MyStreamingMovieViewControllerPlayerItemLoadingObserverContext;

static void *MyStreamingMovieViewControllerPlayerItemDurationObserverContext = &MyStreamingMovieViewControllerPlayerItemDurationObserverContext;

NSString *kTracksKey        = @"tracks";
NSString *kStatusKey        = @"status";
NSString *kLoadingKey       = @"currentItem.loadedTimeRanges";
NSString *kRateKey          = @"rate";
NSString *kPlayableKey      = @"playable";
NSString *kCurrentItemKey   = @"currentItem";
NSString *kTimedMetadataKey = @"currentItem.timedMetadata";
NSString *kDurationKey      = @"currentItem.duration";

//@interface STKM3u8DataSource : STKDataSource
//@property (nonatomic, retain) NSURL* url;
//-(id) initWithURL:(NSURL*)url;
//@end
//
//@implementation STKM3u8DataSource
//
//-(id) initWithURL:(NSURL *)urlIn
//{
//    if (self = [super init])
//    {
//        self.url = urlIn;
//    }
//    return self;
//}
//@end

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
} PlaybackState;

@interface NSArray (ShuffledArray)
- (NSArray *)shuffled;
@end

@implementation NSArray (ShuffledArray)

- (NSArray *)shuffled {
    NSMutableArray *tmpArray = [NSMutableArray arrayWithCapacity:[self count]];
    
    for (id anObject in self) {
        NSUInteger randomPos = arc4random()%([tmpArray count]+1);
        [tmpArray insertObject:anObject atIndex:randomPos];
    }
    
    return [NSArray arrayWithArray:tmpArray];
}

@end


@interface TiMediaAudioStreamerProxy()
@property (strong, nonatomic) NSArray *originalQueue;
@property (strong, nonatomic, readwrite) NSArray *queue;
@property (strong, nonatomic, readwrite) AVPlayerItem *nowPlayingItem;
@property (nonatomic, readwrite) NSUInteger indexOfNowPlayingItem;
@property (nonatomic) BOOL interrupted;
@property (nonatomic) BOOL isLoadingAsset;
@property (nonatomic) MPMusicPlaybackState playbackState;
@property (nonatomic) MPMusicRepeatMode repeatMode; // note: MPMusicRepeatModeDefault is not supported
@property (nonatomic) MPMusicShuffleMode shuffleMode; // note: only MPMusicShuffleModeOff and MPMusicShuffleModeSongs are supported
@property (nonatomic) BOOL shouldReturnToBeginningWhenSkippingToPreviousItem; // default YES

@end

@class TiMediaSoundProxy;
@implementation TiMediaAudioStreamerProxy{
@private
    double _duration;
    double _currentProgress;
//    STKAudioPlayer* player;
//    NSTimer *timer;
    NSMutableArray* _playlist;
//    NSInteger _playPos;
    BOOL _playerInitialized;
    int _state;
    id _currentItem;
//    int _repeatMode;
//    int _shuffleMode;
    ImageLoaderRequest *urlRequest;
//    MPMoviePlayerController *moviePlayer;
    AVPlayer *player;
//    AVPlayerLayer* _playerLayer;
    float volume;
    id timeObserver;
}
//@synthesize currentPlaybackTime = _currentPlayback    Time;
//@synthesize currentPlaybackRate = _currentPlaybackRate;
#pragma mark Internal

void audioRouteChangeListenerCallback (void *inUserData, AudioSessionPropertyID inPropertyID, UInt32 inPropertyValueSize, const void *inPropertyValue) {
    if (inPropertyID != kAudioSessionProperty_AudioRouteChange) return;
    
    TiMediaAudioStreamerProxy* streamer = (__bridge TiMediaAudioStreamerProxy *)inUserData;
    
    CFDictionaryRef routeChangeDictionary = inPropertyValue;
    
    CFNumberRef routeChangeReasonRef = CFDictionaryGetValue(routeChangeDictionary, CFSTR (kAudioSession_AudioRouteChangeKey_Reason));
    SInt32 routeChangeReason;
    CFNumberGetValue (routeChangeReasonRef, kCFNumberSInt32Type, &routeChangeReason);
    
    CFStringRef oldRouteRef = CFDictionaryGetValue(routeChangeDictionary, CFSTR (kAudioSession_AudioRouteChangeKey_OldRoute));
    NSString *oldRouteString = (__bridge NSString *)oldRouteRef;
    
    if (routeChangeReason == kAudioSessionRouteChangeReason_OldDeviceUnavailable) {
        if ((streamer.playbackState == MPMusicPlaybackStatePlaying) &&
            (([oldRouteString isEqualToString:@"Headphone"]) ||
             ([oldRouteString isEqualToString:@"LineOut"])))
        {
            // Janking out the headphone will stop the audio.
            [streamer pause:nil];
        }
    }
}

-(void)_initWithProperties:(NSDictionary *)properties
{
//    _playPos = -1;
    _playerInitialized = NO;
    volume = 1.0f;
    self.indexOfNowPlayingItem = NSNotFound;
    self.repeatMode = MPMusicRepeatModeNone;
    self.shuffleMode = MPMusicShuffleModeOff;
    self.shouldReturnToBeginningWhenSkippingToPreviousItem = YES;
    _state = STATE_STOPPED;
//    _repeatMode = REPEAT_NONE;
//    _shuffleMode = SHUFFLE_NONE;
// Handle unplugging of headphones
    AudioSessionAddPropertyListener (kAudioSessionProperty_AudioRouteChange, audioRouteChangeListenerCallback, (__bridge void*)self);
    [self initializeProperty:@"volume" defaultValue:@(volume)];
    dispatch_async(dispatch_get_main_queue(), ^{
        [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(remoteControlEvent:) name:kTiRemoteControlNotification object:nil];
        [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(paused:) name:kTiPausedNotification object:nil];
        [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(resumed:) name:kTiResumedNotification object:nil];
    });
}

-(void)paused:(id)sender
{
    [self removePlayerTimeObserver];
}

-(void)resumed:(id)sender
{
    if (self.nowPlayingItem) {
        [self initProgressTimer];
    }

}


-(void)_destroy
{
    dispatch_sync(dispatch_get_main_queue(), ^{
        [[NSNotificationCenter defaultCenter] removeObserver:self];
    });
	
    if ([[self playing] boolValue]) {
        [self stop:nil];
        [[TiMediaAudioSession sharedSession] stopAudioSession];
    }
//    [player setDelegate:nil];
//    RELEASE_TO_NIL(player);
//    [self cleanMoviePlayer];
    [self cleanAVPlayer];
    [super _destroy];
}

-(NSString*)apiName
{
    return @"Ti.Media.AudioStreamer";
}

-(void)skipToNextItem {
    if (self.indexOfNowPlayingItem == 0 && _state == STATE_STOPPED) {
        [self play:nil];
        return;
    }
    
    if (self.indexOfNowPlayingItem+1 < [self.queue count]) {
        // Play next track
        self.indexOfNowPlayingItem++;
    } else {
        if (self.repeatMode == MPMusicRepeatModeAll) {
            // Wrap around back to the first track
            self.indexOfNowPlayingItem = 0;
        } else {
            if (self.playbackState == MPMusicPlaybackStatePlaying) {
                if (_nowPlayingItem != nil) {
                    //TODO: end of playlist
                }
            }
            NSLog(@"TiMediaAudioStreamer: end of queue reached");
            [self stop:nil];
        }
    }
}

- (void)skipToBeginning {
    [self seek:@(0.0)];
}

- (void)skipToPreviousItem {
    if (self.indexOfNowPlayingItem > 0) {
        self.indexOfNowPlayingItem--;
    } else if (self.shouldReturnToBeginningWhenSkippingToPreviousItem) {
        [self skipToBeginning];
    }
}

#pragma mark - MPMediaPlayback


- (void)prepareToPlay {
    NSLog(@"Not supported");
}

- (void)beginSeekingBackward {
    NSLog(@"Not supported");
}

- (void)beginSeekingForward {
    NSLog(@"Not supported");
}

- (void)endSeeking {
    NSLog(@"Not supported");
}

- (void)setShuffleMode:(MPMusicShuffleMode)shuffleMode {
    _shuffleMode = shuffleMode;
    self.queue = self.originalQueue;
}

- (void)setOriginalQueue:(NSArray *)originalQueue {
    // The original queue never changes, while queue is shuffled
    RELEASE_TO_NIL(_originalQueue);
    _originalQueue = [originalQueue retain];
    self.queue = originalQueue;
}

- (void)setQueue:(NSArray *)queue {
    RELEASE_TO_NIL(_queue);
    switch (self.shuffleMode) {
        case MPMusicShuffleModeOff:
            _queue = queue;
            break;
            
        case MPMusicShuffleModeSongs:
            _queue = [[queue shuffled] retain];
            break;
            
        default:
            NSLog(@"Only MPMusicShuffleModeOff and MPMusicShuffleModeSongs are supported");
            _queue = [[queue shuffled]retain];
            break;
    }
}

- (void)setIndexOfNowPlayingItem:(NSUInteger)indexOfNowPlayingItem {
    if (indexOfNowPlayingItem == NSNotFound) {
        return;
    }
    
    _indexOfNowPlayingItem = indexOfNowPlayingItem;
    self.nowPlayingItem = [self.queue objectAtIndex:indexOfNowPlayingItem];
}

- (void)setNowPlayingItem:(id)item {
    
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
    
    NSMutableDictionary* myDict = [NSMutableDictionary dictionaryWithObjectsAndKeys:@(AVAssetReferenceRestrictionForbidNone),
                                   AVURLAssetReferenceRestrictionsKey, nil];
    AVURLAsset *asset = [AVURLAsset URLAssetWithURL:url options:myDict];
    
    NSArray *requestedKeys = [NSArray arrayWithObjects:kTracksKey, kPlayableKey, nil];
    
    self.isLoadingAsset = YES;
    /* Tells the asset to load the values of any of the specified keys that are not already loaded. */
    [asset loadValuesAsynchronouslyForKeys:requestedKeys completionHandler:
     ^{
         dispatch_async( dispatch_get_main_queue(),
                        ^{
                            /* IMPORTANT: Must dispatch to main queue in order to operate on the AVPlayer and AVPlayerItem. */
                            [self prepareToPlayAsset:asset withKeys:requestedKeys];
                        });
     }];

    
    // Used to prevent duplicate notifications
    
}

- (void)playItemAtIndex:(NSUInteger)index {
    [self setIndexOfNowPlayingItem:index];
}

- (void)handleAVPlayerItemDidPlayToEndTimeNotification {

    _duration = 0;
    _currentProgress = 0;
    if (!self.isLoadingAsset) {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (self.repeatMode == MPMusicRepeatModeOne) {
                // Play the same track again
                self.indexOfNowPlayingItem = self.indexOfNowPlayingItem;
                if (self.playbackState == MPMusicPlaybackStatePlaying) {
                    [player play];
                }
            } else {
                // Go to next track
                [self skipToNextItem];
                if (self.playbackState == MPMusicPlaybackStatePlaying) {
                    [player play];
                }
            }
        });
    }
}

//-(STKAudioPlayer*)player
//{
//	if (player==nil)
//	{
//        [self cleanMoviePlayer];
//        [self cleanAVPlayer];
//		player = [[STKAudioPlayer alloc] initWithOptions:(STKAudioPlayerOptions){
//            .flushQueueOnSeek = YES,
//            .enableVolumeMixer = YES,
//        }];
//        [player setVolume:volume];
//		[player setDelegate:self];
//        [player setVolume:[TiUtils doubleValue:[self valueForKey:@"volume"] def:1.0f]];
//	}
//	return player;
//}
//
//-(void)cleanMoviePlayer {
//    if (moviePlayer != nil)
//    {
//        NSNotificationCenter* center = [NSNotificationCenter defaultCenter];
//        [center removeObserver:self name:MPMoviePlayerLoadStateDidChangeNotification object:nil];
//        [center removeObserver:self name:MPMoviePlayerPlaybackDidFinishNotification object:nil];
//        [center removeObserver:self name:MPMoviePlayerNowPlayingMovieDidChangeNotification object:nil];
//        [center removeObserver:self name:MPMovieDurationAvailableNotification object:nil];
//        [center removeObserver:self name:MPMoviePlayerTimedMetadataUpdatedNotification object:nil];
////        [moviePlayer.view removeFromSuperview];
//        [moviePlayer pause];
//        [self updateState:[self stateFromMPState:moviePlayer.playbackState]];
//        RELEASE_TO_NIL(moviePlayer)
//    }
//}
//
//-(MPMoviePlayerController*)moviePlayerWithUrl:(NSURL*)url
//{
//    if (moviePlayer==nil)
//    {
//        if (player) {
//            [player stop];
//        }
//        [self cleanAVPlayer];
//        moviePlayer = [[MPMoviePlayerController alloc] initWithContentURL:url];
//        moviePlayer.allowsAirPlay = YES;
//        moviePlayer.shouldAutoplay = YES;
////        moviePlayer.fullscreen = YES;
//        moviePlayer.view.hidden = YES;
//        [moviePlayer setMovieSourceType:MPMovieSourceTypeStreaming];
////        [[TiApp app].window addSubview: moviePlayer.view];
//        [[MPMusicPlayerController applicationMusicPlayer] setVolume:volume];
//        NSNotificationCenter* center = [NSNotificationCenter defaultCenter];
//        [center addObserver:self selector:@selector(moviePlaybackStateChanged:) name:MPMoviePlayerLoadStateDidChangeNotification object:nil];
//        [center addObserver:self selector:@selector(moviePlayBackDidFinish:) name:MPMoviePlayerPlaybackDidFinishNotification object:nil];
//        [center addObserver:self selector:@selector(moviePlayBackDidStart:) name:MPMoviePlayerNowPlayingMovieDidChangeNotification object:nil];
//        [center addObserver:self selector:@selector(moviePlayBackDurationAvailable:) name:MPMovieDurationAvailableNotification object:nil];
//        [center addObserver:self selector:@selector(movieMetadataUpdate:) name:MPMoviePlayerTimedMetadataUpdatedNotification object:nil];
//    }
//    return moviePlayer;
//}


-(void)cleanAVPlayer {
    if (player != nil)
    {
        [self removePlayerTimeObserver];
        [player pause];
        
        [self updateStateForPlayer:player];
        [[NSNotificationCenter defaultCenter] removeObserver:self
                                                        name:AVPlayerItemDidPlayToEndTimeNotification
                                                      object:nil];
        [player removeObserver:self forKeyPath:kCurrentItemKey];
        [player removeObserver:self forKeyPath:kDurationKey];
        [player removeObserver:self forKeyPath:kLoadingKey];
        [player removeObserver:self forKeyPath:kTimedMetadataKey];
        [player removeObserver:self forKeyPath:kRateKey];
        RELEASE_TO_NIL(player)
    }
//    if (_playerLayer) {
//        [_playerLayer removeFromSuperlayer];
//        RELEASE_TO_NIL(_playerLayer)
//    }
}

-(AVPlayer*)player {
    if (!player)
    {
        /* Get a new AVPlayer initialized to play the specified player item. */
        player = [[AVPlayer playerWithPlayerItem:nil] retain];
        [player setVolume:[TiUtils doubleValue:[self valueForKey:@"volume"] def:1.0f]];

//        avPlayerLayer.hidden = YES;
        /* Observe the AVPlayer "currentItem" property to find out when any
         AVPlayer replaceCurrentItemWithPlayerItem: replacement will/did
         occur.*/
        [player addObserver:self
                 forKeyPath:kCurrentItemKey
                    options:NSKeyValueObservingOptionInitial | NSKeyValueObservingOptionNew
                    context:MyStreamingMovieViewControllerCurrentItemObservationContext];
        
        /* A 'currentItem.timedMetadata' property observer to parse the media stream timed metadata. */
        [player addObserver:self
                 forKeyPath:kTimedMetadataKey
                    options:0
                    context:MyStreamingMovieViewControllerTimedMetadataObserverContext];
        
        [player addObserver:self
                 forKeyPath:kDurationKey
                    options:NSKeyValueObservingOptionInitial | NSKeyValueObservingOptionNew
                    context:MyStreamingMovieViewControllerPlayerItemDurationObserverContext];
        [player addObserver:self
                 forKeyPath:kLoadingKey
                    options:NSKeyValueObservingOptionInitial | NSKeyValueObservingOptionNew
                    context:MyStreamingMovieViewControllerPlayerItemLoadingObserverContext];
        
        /* Observe the AVPlayer "rate" property to update the scrubber control. */
        [player addObserver:self
                 forKeyPath:kRateKey
                    options:NSKeyValueObservingOptionInitial | NSKeyValueObservingOptionNew
                    context:MyStreamingMovieViewControllerRateObservationContext];
        _playerInitialized = YES;
    }
    return player;
}


-(void)initProgressTimer
{
    if (timeObserver) {
        return;
    }
    /* Update the scrubber during normal playback. */
    timeObserver = [[[self player] addPeriodicTimeObserverForInterval:CMTimeMake(1, 10)
                                                         queue:NULL
                                                    usingBlock:
                     ^(CMTime time)
                     {
                         double progress = CMTimeGetSeconds(time);
                         if (CMTIME_IS_VALID(time) && _currentProgress > progress || progress - _currentProgress >= 1) {
                             _currentProgress = progress;
                             if (_duration > 0 && [self _hasListeners:@"progress"])
                             {
                                 NSDictionary *event = @{
                                                         @"progress":@(_currentProgress*1000),
                                                         @"duration":@(_duration)
                                                         };
                                 [self fireEvent:@"progress" withObject:event checkForListener:NO];
                             }
                         }
                         
                     }] retain];
}

/* Cancels the previously registered time observer. */
-(void)removePlayerTimeObserver
{
    if (timeObserver)
    {
        [player removeTimeObserver:timeObserver];
        RELEASE_TO_NIL(timeObserver)
    }
}

- (void)prepareToPlayAsset:(AVURLAsset *)asset withKeys:(NSArray *)requestedKeys
{
    /* Make sure that the value of each key has loaded successfully. */
    for (NSString *thisKey in requestedKeys)
    {
        NSError *error = nil;
        AVKeyValueStatus keyStatus = [asset statusOfValueForKey:thisKey error:&error];
        if (keyStatus == AVKeyValueStatusFailed)
        {
            [self assetFailedToPrepareForPlayback:error];
            return;
        }
        /* If you are also implementing the use of -[AVAsset cancelLoading], add your code here to bail
         out properly in the case of cancellation. */
    }
    
    /* Use the AVAsset playable property to detect whether the asset can be played. */
    if (!asset.playable)
    {
        /* Generate an error describing the failure. */
        NSString *localizedDescription = NSLocalizedString(@"Item cannot be played", @"Item cannot be played description");
        NSString *localizedFailureReason = NSLocalizedString(@"The assets tracks were loaded, but could not be made playable.", @"Item cannot be played failure reason");
        NSDictionary *errorDict = [NSDictionary dictionaryWithObjectsAndKeys:
                                   localizedDescription, NSLocalizedDescriptionKey,
                                   localizedFailureReason, NSLocalizedFailureReasonErrorKey,
                                   nil];
        NSError *assetCannotBePlayedError = [NSError errorWithDomain:@"TiMediaAudioStreamer" code:0 userInfo:errorDict];
        
        /* Display the error to the user. */
        [self assetFailedToPrepareForPlayback:assetCannotBePlayedError];
      
        return;
    }
    
    /* Stop observing our prior AVPlayerItem, if we have one. */
    if (_nowPlayingItem)
    {
        /* Remove existing player item key value observers and notifications. */
        
        [_nowPlayingItem removeObserver:self forKeyPath:kStatusKey];
//        [_nowPlayingItem removeObserver:self forKeyPath:kLoadingKey];
        
        [[NSNotificationCenter defaultCenter] removeObserver:self
                                                        name:AVPlayerItemDidPlayToEndTimeNotification
                                                      object:_nowPlayingItem];
    }
    
    /* Create a new instance of AVPlayerItem from the now successfully loaded AVAsset. */
    _nowPlayingItem = [[AVPlayerItem playerItemWithAsset:asset] retain];
    
    /* Observe the player item "status" key to determine when it is ready to play. */
    [_nowPlayingItem addObserver:self
                      forKeyPath:kStatusKey
                         options:NSKeyValueObservingOptionInitial | NSKeyValueObservingOptionNew
                         context:MyStreamingMovieViewControllerPlayerItemStatusObserverContext];
    
    /* Observe the player item "buffering" key. */
//    [_nowPlayingItem addObserver:self
//                      forKeyPath:kLoadingKey
//                         options:NSKeyValueObservingOptionInitial | NSKeyValueObservingOptionNew
//                         context:MyStreamingMovieViewControllerPlayerItemLoadingObserverContext];
    
    
    /* When the player item has played to its end time we'll toggle
     the movie controller Pause button to be the Play button */
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(handleAVPlayerItemDidPlayToEndTimeNotification:)
                                                 name:AVPlayerItemDidPlayToEndTimeNotification
                                               object:_nowPlayingItem];
    
    /* Make our new AVPlayerItem the AVPlayer's current item. */
    [self cleanAVPlayer];
    if ([self player].currentItem != _nowPlayingItem)
    {
//        if (!_playerLayer) {
//            _playerLayer = [[AVPlayerLayer playerLayerWithPlayer:player] retain];
//            _playerLayer.frame = [TiApp app].window.bounds;
//            [[TiApp app].window.layer addSublayer:_playerLayer];
//        }
        
        /* Replace the player item with a new player item. The item replacement occurs
         asynchronously; observe the currentItem property to find out when the
         replacement will/did occur*/
        [player replaceCurrentItemWithPlayerItem:_nowPlayingItem];
    }
    [self initProgressTimer];
    self.isLoadingAsset = NO;
    [player play];
}

#pragma mark Public APIs

-(void)setPaused:(NSNumber *)paused
{
//    if (moviePlayer) {
//        [moviePlayer pause];
//    }
//    else if (avPlayer) {
//        [avPlayer pause];
//    }
//    else if (player)
//	{
		if ([TiUtils boolValue:paused])
		{
            [self pause:nil];
		}
		else
		{
            [self play:nil];
		}
//	}
}


-(void)setMute:(NSNumber *)mute
{
//    if (moviePlayer) {
//        [[MPMusicPlayerController applicationMusicPlayer] setVolume:[TiUtils boolValue:mute]?0:volume];
//    }
//    else if (avPlayer) {
//        avPlayer.muted = [TiUtils boolValue:mute];
//    }
//    else
    [[self player] setMuted:[TiUtils boolValue:mute]];
}

#define PROP_BOOL(name,func) \
-(NSNumber*)name\
{\
return [self func:nil];\
}

-(id)progress
{
    if (player) {
        return @(player.currentTime.epoch);
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
    if (player) {
        return @(player.muted);
    }
    return [self valueForKey:@"muted"];
}
PROP_BOOL(muted,isMute);


-(id)currentItem
{
    return [self getCurrentQueueItem];
}

-(NSNumber *)duration
{
	return @(_duration);
}


-(id)index
{
    return @(self.indexOfNowPlayingItem);
}


-(NSNumber *)volume
{
	return @(volume);
}

-(void)setVolume:(NSNumber *)newVolume
{
	if (player != nil) {
		[player setVolume:volume];
	}
}


-(void)setPlaylist:(id)args
{
    [self stop:nil];
    
    if (IS_OF_CLASS(args, NSArray)) {
        [self setOriginalQueue:[NSMutableArray arrayWithArray:args]];
    }
    else {
        [self setOriginalQueue:[NSMutableArray arrayWithObject:args]];
    }
}


-(void)play:(id)args
{
    [self internalPlayOrResume];
}

-(id)getCurrentQueueItem {
    if (_queue) {
        NSInteger index = self.indexOfNowPlayingItem;
        if (index >=0 && index < [_queue count]) {
            return [_queue objectAtIndex:index];
        }
    }
    return nil;
}

-(void)internalPlayOrResume {
    // indicate we're going to start playing
    if (![[self stopped] boolValue])
    {
//        if (moviePlayer) {
//            [moviePlayer play];
//            [self updateStateForPlayer:moviePlayer];
//        } else if (avPlayer) {
            [player play];
            [self updateStateForPlayer:player];
//        } else {
//            [[self player] resume];
//        }
    } else {
        if (![[TiMediaAudioSession sharedSession] canPlayback]) {
            [self throwException:@"Improper audio session mode for playback"
                       subreason:[[TiMediaAudioSession sharedSession] sessionMode]
                        location:CODELOCATION];
        }
        if ([_queue count]) {
            self.indexOfNowPlayingItem = 0;
        } else {
            self.indexOfNowPlayingItem = NSNotFound;
        }
    }
    
}

-(void)start:(id)args
{
    [self internalPlayOrResume];
}

-(void)stop:(id)args
{
    if (player) {
        [player pause];
        [player seekToTime:CMTimeMakeWithSeconds(0, 1)];
    }
    [self updateState:STATE_STOPPED];
}

-(void)pause:(id)args
{
    
    if (player) {
        [player pause];
        [self updateStateForPlayer:player];
    }
    else {
        [self updateState:STATE_PAUSED];
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
    [self skipToNextItem];
}

-(void)previous:(id)args
{
    if (_state == STATE_PLAYING) {
        double progress = [[self progress] doubleValue];
        if (progress > 2000) {
            if (player) {
                [player seekToTime:CMTimeMakeWithSeconds(0, 1)];
            }
            [self play:nil];
            return;
        }
    }
    [self skipToPreviousItem];
}

-(void)seek:(id)args {
    ENSURE_SINGLE_ARG_OR_NIL(args, NSNumber)
    double time = [TiUtils doubleValue:args]/1000;
    _currentProgress = 0;
    if (player) {
        [player seekToTime:CMTimeMakeWithSeconds(time, 1)];
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
    if (!obj) return nil;
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
    if (_duration > 0) {
        [mediaInfo setObject:@(_duration) forKey:MPMediaItemPropertyPlaybackDuration];
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

- (CMTime)playerItemDuration
{
    return [self itemDuration:[player currentItem]];
}

- (CMTime)itemDuration:(AVPlayerItem *)thePlayerItem
{
    AVPlayerItemStatus status = thePlayerItem.status;
    CMTime duration = [thePlayerItem duration];
    if (CMTIME_IS_NUMERIC(duration)) {
        return(duration);
    }
//    if (status == AVPlayerItemStatusReadyToPlay)
//    {
//        /*
//         NOTE:
//         Because of the dynamic nature of HTTP Live Streaming Media, the best practice
//         for obtaining the duration of an AVPlayerItem object has changed in iOS 4.3.
//         Prior to iOS 4.3, you would obtain the duration of a player item by fetching
//         the value of the duration property of its associated AVAsset object. However,
//         note that for HTTP Live Streaming Media the duration of a player item during
//         any particular playback session may differ from the duration of its asset. For
//         this reason a new key-value observable duration property has been defined on
//         AVPlayerItem.
//         
//         See the AV Foundation Release Notes for iOS 4.3 for more information.
//         */
//        
//        return(duration);
//    }
    
    return(kCMTimeZero);
}


MAKE_SYSTEM_PROP(STATE_INITIALIZED,STATE_INITIALIZED);
MAKE_SYSTEM_PROP(STATE_ERROR,STATE_ERROR);
MAKE_SYSTEM_PROP(STATE_PLAYING,STATE_PLAYING);
MAKE_SYSTEM_PROP(STATE_BUFFERING,STATE_BUFFERING);
MAKE_SYSTEM_PROP(STATE_STOPPED,STATE_STOPPED);
MAKE_SYSTEM_PROP(STATE_PAUSED,STATE_PAUSED);

-(int)stateFromAVPlayerStatus:(AVPlayerStatus)state {
    switch(state)
    {
        case AVPlayerStatusFailed:
            return STATE_ERROR;
        case AVPlayerStatusUnknown:
            return [self stateFromRate:player.rate];
        case AVPlayerStatusReadyToPlay:
            return (_state == STATE_PLAYING)?STATE_PLAYING:STATE_INITIALIZED;
//            if (state == STATE_PLAYING) {
//                return state;
//            }
    }
}


-(int)stateFromRate:(float)rate {
    if (rate == 0.0f) {
        return STATE_PAUSED;
    }
    return STATE_PLAYING;
}

-(void)updateStateForPlayer:(AVPlayer*)thePlayer {
    if (!thePlayer) {
        [self updateState:STATE_STOPPED];
    }
    else {
//    else if (thePlayer == avPlayer) {
        [self updateState:[self stateFromRate:thePlayer.rate]];
//    } else if (thePlayer == moviePlayer) {
//        [self updateState:[self stateFromMPState:moviePlayer.playbackState]];
//    }
    }
}

-(void)updateState:(int)newState {
    if (_state != newState) {
        _state = newState;
        if (_state == STATE_PLAYING) {
            [[TiMediaAudioSession sharedSession] startAudioSession];
        } else {
            if (_state == STATE_STOPPED || _state == STATE_ERROR) {
                _duration = 0;
                _currentProgress = 0;
                [self removePlayerTimeObserver];
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

//- (void)updateProgress:(NSTimer *)updatedTimer
//{
//    [self updateDuration];
//    if ([self _hasListeners:@"progress"])
//    {
//        NSDictionary *event = @{
//                            @"progress":[self progress],
//                            @"duration":@(_duration)
//                            };
//        [self fireEvent:@"progress" withObject:event checkForListener:NO];
//    }
//}

- (void)remoteControlEvent:(NSNotification*)note
{
    if (!player || _state == STATE_STOPPED || _state == STATE_ERROR) {
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

#pragma mark AVPlayer Notifications

- (void)observeValueForKeyPath:(NSString*) path
                      ofObject:(id)object
                        change:(NSDictionary*)change
                       context:(void*)context
{
    /* AVPlayerItem "status" property value observer. */
    if (context == MyStreamingMovieViewControllerPlayerItemStatusObserverContext)
    {
        AVPlayerStatus status = [[change objectForKey:NSKeyValueChangeNewKey] integerValue];
        [self updateState:[self stateFromAVPlayerStatus:status]];
        if (status == AVPlayerStatusFailed) {
            
            NSError *error = [_nowPlayingItem error];
            NSLog(@"%s %d: %@\n", __FUNCTION__, [error code], [error description]);
        }
    }
    /* AVPlayer "rate" property value observer. */
    else if (context == MyStreamingMovieViewControllerRateObservationContext)
    {
        [self updateStateForPlayer:player];
    }
    /* AVPlayer "currentItem" property observer.
     Called when the AVPlayer replaceCurrentItemWithPlayerItem:
     replacement will/did occur. */
    else if (context == MyStreamingMovieViewControllerCurrentItemObservationContext)
    {
        
        AVPlayerItem *newPlayerItem = [change objectForKey:NSKeyValueChangeNewKey];
        /* New player item null? */
        _currentProgress = 0;
        if (newPlayerItem == (id)[NSNull null])
        {
            _duration = 0;
        }
        else /* Replacement of player currentItem has occurred */
        {
            _duration = CMTimeGetSeconds([self itemDuration:newPlayerItem])*1000;
        }
        _currentItem = [_queue objectAtIndex:self.indexOfNowPlayingItem];
        if (_currentItem && _playerInitialized) {
            [self updateMetadata];
            if ([self _hasListeners:@"change"])
            {
                NSDictionary *event = @{
                                        @"track":_currentItem,
                                        @"duration":@(_duration),
                                        @"index":@(self.indexOfNowPlayingItem)
                                        };
                [self fireEvent:@"change" withObject:event checkForListener:NO];
            }
        }
        
        [self updateStateForPlayer:player];
    }
    else if (context == MyStreamingMovieViewControllerPlayerItemDurationObserverContext)
    {
        _duration = CMTimeGetSeconds([self playerItemDuration])*1000;
        if (_duration != 0) {
            if ([self _hasListeners:@"progress"])
            {
                NSDictionary *event = @{
                                        @"progress":@(_currentProgress*1000),
                                        @"duration":@(_duration)
                                        };
                [self fireEvent:@"progress" withObject:event checkForListener:NO];
            }
        }
    }
    else if (context == MyStreamingMovieViewControllerPlayerItemLoadingObserverContext)
    {
        NSArray *timeRanges = (NSArray*)[change objectForKey:NSKeyValueChangeNewKey];
        if (timeRanges && (id)timeRanges != [NSNull null] && [timeRanges count]) {
            CMTimeRange timerange=[[timeRanges objectAtIndex:0]CMTimeRangeValue];
            if ([self _hasListeners:@"buffering"])
            {
                float progress = (_duration > 0)?(CMTimeGetSeconds(timerange.duration)*1000 / _duration * 100):0;
                NSDictionary *event = [NSDictionary dictionaryWithObjectsAndKeys:@(progress),@"progress",nil];
                [self fireEvent:@"buffering" withObject:event checkForListener:NO];
            }
        }
    }
    /* Observe the AVPlayer "currentItem.timedMetadata" property to parse the media stream
     timed metadata. */
    else if (context == MyStreamingMovieViewControllerTimedMetadataObserverContext)
    {
        NSArray* array = [_nowPlayingItem timedMetadata];
        for (AVMetadataItem *metadataItem in array)
        {
            [self handleTimedMetadata:metadataItem];
        }
    }
    
    else
    {
        [super observeValueForKeyPath:path ofObject:object change:change context:context];
    }
    
    return;
}

-(void)assetFailedToPrepareForPlayback:(NSError *)error
{
    self.isLoadingAsset = NO;
    [self removePlayerTimeObserver];
}

#pragma mark -
#pragma mark Timed metadata
#pragma mark -

- (void)handleTimedMetadata:(AVMetadataItem*)timedMetadata
{
    NSLog(@"metadata: key = %@", [timedMetadata key]);
    id value = [timedMetadata value];
    NSLog(@"metadata: value = %@", value);
    /* We expect the content to contain plists encoded as timed metadata. AVPlayer turns these into NSDictionaries. */
    if ([(NSString *)[timedMetadata key] isEqualToString:AVMetadataID3MetadataKeyGeneralEncapsulatedObject])
    {
        if ([[timedMetadata value] isKindOfClass:[NSDictionary class]])
        {
            NSDictionary *propertyList = (NSDictionary *)[timedMetadata value];
        }
    }
}


@end

#endif