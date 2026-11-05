package com.sonyericsson.android.camera.recorder.defaultrecorder;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import com.sonyericsson.android.camera.device.CameraActionSound;
import com.sonyericsson.android.camera.recorder.RecorderController;
import com.sonyericsson.android.camera.recorder.RecorderException;
import com.sonyericsson.android.camera.recorder.RecorderInterface;
import com.sonyericsson.android.camera.recorder.RecorderParameters;
import com.sonyericsson.android.camera.recorder.utility.Accessor;
import com.sonyericsson.android.camera.recorder.utility.ReferenceClock;
import com.sonyericsson.android.camera.util.CamLog;
import com.sonyericsson.cameracommon.storage.Storage;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BaseRecorderController implements RecorderController {
    private static final int TIME_OF_START_SOUND_TO_COMPLETE_IN_MILLI = 300;
    private static final boolean TRACE = true;
    private static final boolean TRACE_FOR_PROGRESS = false;
    private final Handler mCallbackHandler;
    private final Accessor<CameraActionSound> mCameraActionSound;
    private final Context mContext;
    private final Handler mDeviceHandler;
    private boolean mIsAdjustRecordingTimeByRecorderNotification;
    protected boolean mIsCameraErrorDetected;
    private boolean mIsMicrophoneEnabled;
    private final boolean mIsStartSoundRequired;
    private boolean mIsStopSoundAlreadyPlayed;
    private final boolean mIsStopSoundRequired;
    private boolean mIsUserSoundSettingOn;
    private long mLastNotifyDurationMillis;
    private final RecorderController.RecorderListener mListener;
    private long mMaxDurationMillis;
    private final long mMinDurationMillis;
    private final RecorderInterface mRecorder;
    private final ReferenceClock mReferenceClock;
    private final boolean mShouldWaitStartSound;
    private State mState;
    private Storage.StorageWriteNotifier mStorageWriteNotifier;
    private CountDownLatch mWaitUntilWriting;
    protected final Object mStateLock = new Object();
    private final Object mIsStopSoundAlreadyPlayedLock = new Object();
    private final Runnable mReleaseTask = new Runnable() { // from class: com.sonyericsson.android.camera.recorder.defaultrecorder.BaseRecorderController.1
        @Override // java.lang.Runnable
        public void run() {
            synchronized (BaseRecorderController.this.mStateLock) {
                if (BaseRecorderController.this.verifyState(State.RELEASED)) {
                    BaseRecorderController.trace("release() X already released on the other");
                    return;
                }
            }
            BaseRecorderController.this.releaseInternal();
            synchronized (BaseRecorderController.this.mStateLock) {
                BaseRecorderController.this.changeTo(State.RELEASED);
            }
        }
    };
    private final RecorderInterface.OnErrorListener mOnErrorListener = new RecorderInterface.OnErrorListener() { // from class: com.sonyericsson.android.camera.recorder.defaultrecorder.BaseRecorderController.2
        @Override // com.sonyericsson.android.camera.recorder.RecorderInterface.OnErrorListener
        public void onError() {
            BaseRecorderController.this.notifyError();
        }
    };
    private final RecorderInterface.OnMaxReachedListener mOnMaxReachedListener = new RecorderInterface.OnMaxReachedListener() { // from class: com.sonyericsson.android.camera.recorder.defaultrecorder.BaseRecorderController.3
        @Override // com.sonyericsson.android.camera.recorder.RecorderInterface.OnMaxReachedListener
        public void onMaxDurationReached() {
            BaseRecorderController.trace("onMaxDurationReached() E");
            BaseRecorderController.this.displayMaxDuration();
            BaseRecorderController.this.notifyFinishResult(RecorderController.Result.MAX_DURATION_REACHED);
            BaseRecorderController.trace("onMaxDurationReached() X");
        }

        @Override // com.sonyericsson.android.camera.recorder.RecorderInterface.OnMaxReachedListener
        public void onMaxFileSizeReached() {
            BaseRecorderController.trace("onMaxFileSizeReached() E");
            BaseRecorderController.this.notifyFinishResult(RecorderController.Result.MAX_FILESIZE_REACHED);
            BaseRecorderController.trace("onMaxFileSizeReached() X");
        }
    };
    private final RecorderInterface.RecordTrackListener mAudioTrackListener = new RecorderInterface.RecordTrackListener() { // from class: com.sonyericsson.android.camera.recorder.defaultrecorder.BaseRecorderController.4
        @Override // com.sonyericsson.android.camera.recorder.RecorderInterface.RecordTrackListener
        public void onStarted() {
            BaseRecorderController.trace("onStarted() E: Audio Track");
            if (BaseRecorderController.this.mWaitUntilWriting != null && BaseRecorderController.this.mWaitUntilWriting.getCount() > 0) {
                BaseRecorderController.this.mWaitUntilWriting.countDown();
            }
            BaseRecorderController.trace("onStarted() X: Audio Track");
        }

        @Override // com.sonyericsson.android.camera.recorder.RecorderInterface.RecordTrackListener
        public void onProgress(long j) {
            BaseRecorderController.this.notifyDuration(j);
        }

        @Override // com.sonyericsson.android.camera.recorder.RecorderInterface.RecordTrackListener
        public void onCompleted() {
            BaseRecorderController.trace("onCompleted() E: Audio Track");
            BaseRecorderController.this.playStopSound();
            BaseRecorderController.trace("onCompleted() X: Audio Track");
        }
    };
    private final RecorderInterface.RecordTrackListener mVideoTrackListener = new RecorderInterface.RecordTrackListener() { // from class: com.sonyericsson.android.camera.recorder.defaultrecorder.BaseRecorderController.5
        @Override // com.sonyericsson.android.camera.recorder.RecorderInterface.RecordTrackListener
        public void onStarted() {
            BaseRecorderController.trace("onStarted() E: Video Track");
            if (BaseRecorderController.this.mWaitUntilWriting != null && BaseRecorderController.this.mWaitUntilWriting.getCount() > 0) {
                BaseRecorderController.this.mWaitUntilWriting.countDown();
            }
            BaseRecorderController.trace("onStarted() X: Video Track");
        }

        @Override // com.sonyericsson.android.camera.recorder.RecorderInterface.RecordTrackListener
        public void onProgress(long j) {
            BaseRecorderController.this.notifyDuration(j);
        }

        @Override // com.sonyericsson.android.camera.recorder.RecorderInterface.RecordTrackListener
        public void onCompleted() {
            BaseRecorderController.trace("onCompleted() E: Video Track");
            BaseRecorderController.trace("onCompleted() X: Video Track");
        }
    };
    private final ReferenceClock.TickCallback mOnTickCallback = new ReferenceClock.TickCallback() { // from class: com.sonyericsson.android.camera.recorder.defaultrecorder.BaseRecorderController.6
        @Override // com.sonyericsson.android.camera.recorder.utility.ReferenceClock.TickCallback
        public void onTick(long j) {
            synchronized (BaseRecorderController.this.mStateLock) {
                if (BaseRecorderController.this.verifyState(State.IDLE, State.RELEASING)) {
                    return;
                }
            }
            if (BaseRecorderController.this.mStorageWriteNotifier != null) {
                BaseRecorderController.this.mStorageWriteNotifier.notifyWriteStorage();
            }
            BaseRecorderController.this.mListener.onRecordProgress(j);
        }
    };

    protected enum State {
        IDLE,
        PREPARED,
        STARTING,
        RECORDING,
        PAUSED,
        STOPPING,
        RELEASING,
        RELEASED
    }

    /* JADX INFO: Access modifiers changed from: private */
    private static void trace(String str) {
        CamLog.d(str);
    }

    protected void disableAdjustRecordingTimeByRecorderNotification() {
        this.mIsAdjustRecordingTimeByRecorderNotification = false;
    }

    private boolean shouldPlayStartSound() {
        return this.mIsStartSoundRequired && this.mIsUserSoundSettingOn;
    }

    private boolean shouldPlayStopSound() {
        return this.mIsStopSoundRequired && this.mIsUserSoundSettingOn;
    }

    protected void changeTo(State state) {
        trace("changeTo() " + state.name());
        this.mState = state;
    }

    protected boolean verifyState(State... stateArr) {
        for (State state : stateArr) {
            if (state == this.mState) {
                return true;
            }
        }
        return false;
    }

    protected Context getContext() {
        return this.mContext;
    }

    protected Handler getCallbackHandler() {
        return this.mCallbackHandler;
    }

    protected RecorderInterface getRecorder() {
        return this.mRecorder;
    }

    protected ReferenceClock getReferenceClock() {
        return this.mReferenceClock;
    }

    protected void executeInBackground(Runnable runnable) {
        this.mDeviceHandler.post(runnable);
    }

    public BaseRecorderController(Context context, Accessor<CameraActionSound> accessor, RecorderInterface recorderInterface, Handler handler, RecorderController.RecorderListener recorderListener, long j, int i, Handler handler2, boolean z, boolean z2, boolean z3, boolean z4) {
        trace("BaseRecorderController() E");
        this.mContext = context;
        this.mCameraActionSound = accessor;
        this.mListener = recorderListener;
        this.mCallbackHandler = handler;
        changeTo(State.IDLE);
        this.mReferenceClock = new ReferenceClock(this.mCallbackHandler, this.mOnTickCallback, i);
        this.mDeviceHandler = handler2;
        this.mMinDurationMillis = j;
        this.mIsStartSoundRequired = z;
        this.mShouldWaitStartSound = z2;
        this.mIsStopSoundRequired = z3;
        this.mIsUserSoundSettingOn = z4;
        this.mIsStopSoundAlreadyPlayed = false;
        this.mRecorder = recorderInterface;
        this.mRecorder.setListener(this.mAudioTrackListener, this.mVideoTrackListener, this.mOnErrorListener, this.mOnMaxReachedListener);
        this.mIsAdjustRecordingTimeByRecorderNotification = true;
        trace("BaseRecorderController() X");
    }

    @Override // com.sonyericsson.android.camera.recorder.RecorderController
    public boolean isReady() {
        boolean zVerifyState;
        synchronized (this.mStateLock) {
            zVerifyState = verifyState(State.PREPARED);
        }
        return zVerifyState;
    }

    @Override // com.sonyericsson.android.camera.recorder.RecorderController
    public boolean isPaused() {
        boolean zVerifyState;
        synchronized (this.mStateLock) {
            zVerifyState = verifyState(State.PAUSED);
        }
        return zVerifyState;
    }

    @Override // com.sonyericsson.android.camera.recorder.RecorderController
    public boolean isStarting() {
        boolean zVerifyState;
        synchronized (this.mStateLock) {
            zVerifyState = verifyState(State.STARTING);
        }
        return zVerifyState;
    }

    @Override // com.sonyericsson.android.camera.recorder.RecorderController
    public boolean isRecording() {
        boolean zVerifyState;
        synchronized (this.mStateLock) {
            zVerifyState = verifyState(State.STARTING, State.RECORDING);
        }
        return zVerifyState;
    }

    @Override // com.sonyericsson.android.camera.recorder.RecorderController
    public boolean isStopping() {
        boolean zVerifyState;
        synchronized (this.mStateLock) {
            zVerifyState = verifyState(State.STOPPING, State.RELEASING);
        }
        return zVerifyState;
    }

    @Override // com.sonyericsson.android.camera.recorder.RecorderController
    public long getRecordingTimeMillis() {
        return this.mReferenceClock.elapsedTimeMillis();
    }

    private CameraActionSound getCameraActionSound() {
        return this.mCameraActionSound.get();
    }

    protected void playStartSound() {
        trace("playStartSound() E required:" + shouldPlayStartSound());
        if (!shouldPlayStartSound()) {
            trace("playStartSound() X not required");
            return;
        }
        CameraActionSound cameraActionSound = getCameraActionSound();
        if (cameraActionSound != null) {
            cameraActionSound.play(2, true);
        }
        trace("playStartSound() X");
    }

    @Override // com.sonyericsson.android.camera.recorder.RecorderController
    public boolean prepare(RecorderParameters recorderParameters) {
        trace("prepare() E");
        this.mIsStopSoundAlreadyPlayed = false;
        this.mLastNotifyDurationMillis = 0L;
        synchronized (this.mStateLock) {
            if (!verifyState(State.IDLE)) {
                trace("prepare() X failed : illegal state");
                return false;
            }
            changeTo(State.PREPARED);
            executeInBackground(new PrepareTask(recorderParameters));
            trace("prepare() X");
            return true;
        }
    }

    private class PrepareTask implements Runnable {
        private final RecorderParameters mParameters;

        public PrepareTask(RecorderParameters recorderParameters) {
            this.mParameters = recorderParameters;
        }

        @Override // java.lang.Runnable
        public void run() {
            synchronized (BaseRecorderController.this.mStateLock) {
                if (BaseRecorderController.this.verifyState(State.RELEASING, State.RELEASED)) {
                    BaseRecorderController.trace("Fail to verify state in PrepareTask. state:" + BaseRecorderController.this.mState.name());
                    return;
                }
            }
            if (!BaseRecorderController.this.prepareInternal(this.mParameters)) {
                BaseRecorderController.this.notifyError();
            }
        }
    }

    protected boolean prepareInternal(RecorderParameters recorderParameters) {
        trace("prepareInternal() E mic:" + recorderParameters.isMicrophoneEnabled());
        this.mIsMicrophoneEnabled = recorderParameters.isMicrophoneEnabled();
        if (recorderParameters.hasMaxDuration()) {
            this.mMaxDurationMillis = recorderParameters.maxDuration();
        } else {
            this.mMaxDurationMillis = 0L;
        }
        this.mRecorder.reset();
        boolean zPrepare = this.mRecorder.prepare(this.mContext, recorderParameters);
        if (!zPrepare) {
            synchronized (this.mStateLock) {
                changeTo(State.RELEASED);
            }
        }
        trace("prepareInternal() X success:" + zPrepare);
        return zPrepare;
    }

    @Override // com.sonyericsson.android.camera.recorder.RecorderController
    public void start() throws RecorderException {
        trace("start() E");
        synchronized (this.mStateLock) {
            if (!verifyState(State.PREPARED)) {
                trace("start() X failed : illegal state");
                throw new RecorderException("Fail to verify state. state:" + this.mState.name());
            }
            playStartSound();
            changeTo(State.STARTING);
            executeInBackground(new StartTask());
        }
        trace("start() X");
    }

    private class StartTask implements Runnable {
        private StartTask() {
        }

        @Override // java.lang.Runnable
        public void run() {
            synchronized (BaseRecorderController.this.mStateLock) {
                if (BaseRecorderController.this.verifyState(State.RELEASING, State.RELEASED)) {
                    BaseRecorderController.trace("Fail to verify state in StartTask. state:" + BaseRecorderController.this.mState.name());
                    return;
                }
            }
            if (BaseRecorderController.this.mShouldWaitStartSound) {
                try {
                    Thread.sleep(300L);
                } catch (InterruptedException unused) {
                    CamLog.w("StartTask interrupted");
                }
            }
            try {
                if (!BaseRecorderController.this.startInternal()) {
                    BaseRecorderController.this.notifyError();
                }
            } catch (TimeoutException e) {
                if (CamLog.DEBUG) {
                    throw new RuntimeException(e);
                }
                BaseRecorderController.this.notifyError();
            }
            synchronized (BaseRecorderController.this.mStateLock) {
                if (BaseRecorderController.this.verifyState(State.STARTING)) {
                    BaseRecorderController.this.changeTo(State.RECORDING);
                }
            }
        }
    }

    private class NotifyProgressTask implements Runnable {
        private final long mRecordingTimeMillis;

        public NotifyProgressTask(long j) {
            this.mRecordingTimeMillis = j;
        }

        @Override // java.lang.Runnable
        public void run() {
            if (BaseRecorderController.this.mStorageWriteNotifier != null) {
                BaseRecorderController.this.mStorageWriteNotifier.notifyWriteStorage();
            }
            BaseRecorderController.this.mListener.onRecordProgress(this.mRecordingTimeMillis);
        }
    }

    protected boolean startInternal() throws TimeoutException {
        trace("startInternal() E");
        this.mWaitUntilWriting = new CountDownLatch(1);
        try {
            this.mRecorder.start();
            this.mCallbackHandler.post(new NotifyProgressTask(0L));
            if (this.mIsAdjustRecordingTimeByRecorderNotification) {
                this.mReferenceClock.reset(0L);
            } else {
                this.mReferenceClock.start();
            }
            trace("startInternal() X");
            return true;
        } catch (IOException | IllegalStateException e) {
            trace("startInternal() X failed : " + e.getMessage());
            changeTo(State.RELEASED);
            this.mRecorder.reset();
            return false;
        }
    }

    @Override // com.sonyericsson.android.camera.recorder.RecorderController
    public void stop() throws RecorderException {
        trace("stop() E");
        synchronized (this.mStateLock) {
            if (!verifyState(State.STARTING, State.RECORDING, State.PAUSED)) {
                trace("stop() X failed : illegal state");
                throw new RecorderException("Fail to verify state. state:" + this.mState.name());
            }
            changeTo(State.STOPPING);
            executeInBackground(new StopTask());
        }
        trace("stop() X");
    }

    @Override // com.sonyericsson.android.camera.recorder.RecorderController
    public void stopAudioRecording() {
        getRecorder().stopAudioRecording();
    }

    @Override // com.sonyericsson.android.camera.recorder.RecorderController
    public void stopOnCameraError() throws RecorderException {
        trace("stopOnCameraError() E");
        this.mIsCameraErrorDetected = true;
        stop();
        trace("stopOnCameraError() X");
    }

    private class StopTask implements Runnable {
        private StopTask() {
        }

        @Override // java.lang.Runnable
        public void run() {
            synchronized (BaseRecorderController.this.mStateLock) {
                if (BaseRecorderController.this.verifyState(State.RELEASED)) {
                    BaseRecorderController.trace("Fail to verify state in StopTask. state:" + BaseRecorderController.this.mState.name());
                    return;
                }
            }
            if (!BaseRecorderController.this.mIsMicrophoneEnabled) {
                BaseRecorderController.this.playStopSound();
            }
            boolean zStopInternal = BaseRecorderController.this.stopInternal();
            BaseRecorderController.this.playStopSound();
            synchronized (BaseRecorderController.this.mStateLock) {
                BaseRecorderController.this.notifyFinishResult(zStopInternal ? RecorderController.Result.SUCCESS : RecorderController.Result.FAIL);
            }
        }
    }

    protected boolean stopInternal() {
        trace("stopInternal() E");
        if (this.mStorageWriteNotifier != null) {
            this.mStorageWriteNotifier = null;
        }
        try {
            try {
                waitUntilFirstVideoFrameWritten();
                this.mReferenceClock.stop();
                if (this.mIsCameraErrorDetected) {
                    this.mRecorder.stopOnCameraError();
                } else {
                    this.mRecorder.stop();
                }
                this.mRecorder.reset();
                trace("stopInternal() X");
                return true;
            } catch (RuntimeException e) {
                trace("stopInternal() X failed : " + e.getMessage());
                this.mRecorder.reset();
                return false;
            }
        } catch (Throwable th) {
            this.mRecorder.reset();
            throw th;
        }
    }

    @Override // com.sonyericsson.android.camera.recorder.RecorderController
    public void pause() throws RecorderException {
        trace("pause() E");
        synchronized (this.mStateLock) {
            if (!verifyState(State.STARTING, State.RECORDING)) {
                trace("pause() X failed : illegal state");
                throw new RecorderException("Fail to verify state. state:" + this.mState.name());
            }
            changeTo(State.PAUSED);
            executeInBackground(new PauseTask());
        }
        trace("pause() X");
    }

    private class PauseTask implements Runnable {
        private PauseTask() {
        }

        @Override // java.lang.Runnable
        public void run() {
            synchronized (BaseRecorderController.this.mStateLock) {
                if (BaseRecorderController.this.verifyState(State.RELEASING, State.RELEASED)) {
                    BaseRecorderController.trace("Fail to verify state in PauseTask. state:" + BaseRecorderController.this.mState.name());
                    return;
                }
            }
            if (!BaseRecorderController.this.pauseInternal()) {
                BaseRecorderController.this.notifyError();
            }
        }
    }

    protected boolean pauseInternal() {
        trace("pauseInternal() E");
        try {
            waitUntilFirstVideoFrameWritten();
            this.mReferenceClock.stop();
            this.mRecorder.pause();
            trace("pauseInternal() X");
            return true;
        } catch (RuntimeException e) {
            trace("pauseInternal() X failed : " + e.getMessage());
            this.mRecorder.reset();
            return false;
        }
    }

    @Override // com.sonyericsson.android.camera.recorder.RecorderController
    public void resume() throws RecorderException {
        trace("resume() E");
        synchronized (this.mStateLock) {
            if (!verifyState(State.PAUSED)) {
                trace("resume() X failed : illegal state");
                throw new RecorderException("Fail to verify state. state:" + this.mState.name());
            }
            changeTo(State.RECORDING);
            executeInBackground(new ResumeTask());
        }
        trace("resume() X");
    }

    private class ResumeTask implements Runnable {
        private ResumeTask() {
        }

        @Override // java.lang.Runnable
        public void run() {
            synchronized (BaseRecorderController.this.mStateLock) {
                if (BaseRecorderController.this.verifyState(State.RELEASING, State.RELEASED)) {
                    BaseRecorderController.trace("Fail to verify state in ResumeTask. state:" + BaseRecorderController.this.mState.name());
                    return;
                }
            }
            if (!BaseRecorderController.this.resumeInternal()) {
                BaseRecorderController.this.notifyError();
            }
        }
    }

    protected boolean resumeInternal() {
        trace("resumeInternal() E");
        try {
            this.mRecorder.resume();
            if (!this.mIsAdjustRecordingTimeByRecorderNotification) {
                this.mReferenceClock.resume();
            }
            trace("resumeInternal() X");
            return true;
        } catch (RuntimeException e) {
            trace("resumeInternal() X failed : " + e.getMessage());
            this.mRecorder.reset();
            return false;
        }
    }

    @Override // com.sonyericsson.android.camera.recorder.RecorderController
    public boolean release() {
        trace("release() E");
        synchronized (this.mStateLock) {
            if (verifyState(State.RELEASING, State.RELEASED)) {
                trace("release() X already released");
                return true;
            }
            if (verifyState(State.STARTING, State.RECORDING, State.PAUSED)) {
                try {
                    stop();
                } catch (RecorderException e) {
                    CamLog.e("release() X failed : " + e.getMessage());
                    return false;
                }
            }
            boolean zVerifyState = verifyState(State.STOPPING, State.IDLE, State.PREPARED);
            changeTo(State.RELEASING);
            if (zVerifyState) {
                executeInBackground(this.mReleaseTask);
            } else {
                synchronized (this.mStateLock) {
                    changeTo(State.RELEASED);
                }
            }
            trace("release() X success");
            return true;
        }
    }

    protected void releaseInternal() {
        this.mRecorder.release();
    }

    @Override // com.sonyericsson.android.camera.recorder.RecorderController
    public void setLocation(Location location) {
        this.mRecorder.setLocation(location);
    }

    @Override // com.sonyericsson.android.camera.recorder.RecorderController
    public void setMaxDurationMillis(long j) {
        this.mRecorder.setMaxDurationMillis(j);
    }

    @Override // com.sonyericsson.android.camera.recorder.RecorderController
    public void setMaxFileSizeBytes(long j) {
        this.mRecorder.setMaxFileSizeBytes(j);
    }

    @Override // com.sonyericsson.android.camera.recorder.RecorderController
    public void setOrientationHint(int i) {
        this.mRecorder.setOrientationHint(i);
    }

    @Override // com.sonyericsson.android.camera.recorder.RecorderController
    public void setOutputFilePath(String str) {
        this.mRecorder.setOutputFilePath(str);
    }

    @Override // com.sonyericsson.android.camera.recorder.RecorderController
    public void setUserSoundSetting(boolean z) {
        this.mIsUserSoundSettingOn = z;
    }

    /* JADX INFO: Access modifiers changed from: private */
    private void playStopSound() {
        CameraActionSound cameraActionSound;
        trace("playStopSound() E required:" + shouldPlayStopSound());
        if (shouldPlayStopSound()) {
            boolean z = false;
            synchronized (this.mIsStopSoundAlreadyPlayedLock) {
                trace("playStopSound() is-already-played:" + this.mIsStopSoundAlreadyPlayed);
                if (!this.mIsStopSoundAlreadyPlayed) {
                    this.mIsStopSoundAlreadyPlayed = true;
                    z = true;
                }
            }
            if (z && (cameraActionSound = getCameraActionSound()) != null) {
                cameraActionSound.play(3, true);
            }
        }
        trace("playStopSound() X");
    }

    protected void waitUntilFirstVideoFrameWritten() {
        trace("waitUntilFirstVideoFrameWritten() E");
        try {
            if (this.mWaitUntilWriting != null) {
                if (!this.mWaitUntilWriting.await(this.mMinDurationMillis, TimeUnit.MILLISECONDS)) {
                    trace("waitUntilFirstVideoFrameWritten() timed-out");
                }
            } else {
                Thread.sleep(this.mMinDurationMillis, 0);
            }
        } catch (InterruptedException unused) {
            trace("waitUntilFirstVideoFrameWritten() interrupted at mWaitUntilWriting.await()");
        }
        trace("waitUntilFirstVideoFrameWritten() X");
    }

    protected void notifyError() {
        this.mCallbackHandler.post(new OnErrorTask());
    }

    private class OnErrorTask implements Runnable {
        private OnErrorTask() {
        }

        @Override // java.lang.Runnable
        public void run() {
            BaseRecorderController.trace("onError() E");
            synchronized (BaseRecorderController.this.mStateLock) {
                if (BaseRecorderController.this.verifyState(State.IDLE, State.RELEASING)) {
                    return;
                }
                BaseRecorderController.this.playStopSound();
                BaseRecorderController.this.mListener.onRecordError(0, 0);
                BaseRecorderController.trace("onError() X");
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    private void notifyFinishResult(RecorderController.Result result) {
        this.mCallbackHandler.post(new NotifyFinishResult(result));
    }

    private class NotifyFinishResult implements Runnable {
        private final RecorderController.Result mResult;

        public NotifyFinishResult(RecorderController.Result result) {
            this.mResult = result;
        }

        @Override // java.lang.Runnable
        public void run() {
            boolean zVerifyState;
            BaseRecorderController.trace("notifyFinishResult() E result:" + this.mResult.name());
            switch (this.mResult) {
                case SUCCESS:
                case FAIL:
                    synchronized (BaseRecorderController.this.mStateLock) {
                        zVerifyState = BaseRecorderController.this.verifyState(State.STOPPING, State.RELEASING, State.RELEASED);
                    }
                    if (zVerifyState) {
                        BaseRecorderController.this.mListener.onRecordFinished(this.mResult);
                    }
                    synchronized (BaseRecorderController.this.mStateLock) {
                        BaseRecorderController.this.mReferenceClock.reset(Math.max(BaseRecorderController.this.mReferenceClock.elapsedTimeMillis(), BaseRecorderController.this.mLastNotifyDurationMillis));
                        if (!BaseRecorderController.this.verifyState(State.RELEASING, State.RELEASED)) {
                            BaseRecorderController.this.changeTo(State.IDLE);
                        }
                    }
                    break;
                case MAX_DURATION_REACHED:
                case MAX_FILESIZE_REACHED:
                    BaseRecorderController.this.mListener.onRecordFinished(this.mResult);
                    break;
            }
            BaseRecorderController.trace("notifyFinishResult() X");
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    private void notifyDuration(long j) {
        this.mLastNotifyDurationMillis = j;
        if (!this.mIsAdjustRecordingTimeByRecorderNotification || this.mReferenceClock.isMeasuring()) {
            return;
        }
        synchronized (this.mStateLock) {
            if (verifyState(State.STARTING, State.RECORDING)) {
                this.mReferenceClock.reset(j);
                this.mReferenceClock.resume();
            }
        }
        if (this.mStorageWriteNotifier != null) {
            this.mStorageWriteNotifier.notifyWriteStorage();
        }
        this.mListener.onRecordProgress(j);
    }

    /* JADX INFO: Access modifiers changed from: private */
    private void displayMaxDuration() {
        if (this.mMaxDurationMillis <= 0 || this.mMaxDurationMillis - this.mLastNotifyDurationMillis < 0 || this.mMaxDurationMillis - this.mLastNotifyDurationMillis >= 1000) {
            return;
        }
        this.mCallbackHandler.post(new Runnable() { // from class: com.sonyericsson.android.camera.recorder.defaultrecorder.BaseRecorderController.7
            @Override // java.lang.Runnable
            public void run() {
                BaseRecorderController.this.mOnTickCallback.onTick(BaseRecorderController.this.mMaxDurationMillis);
            }
        });
    }

    @Override // com.sonyericsson.android.camera.recorder.RecorderController
    public void setStorageWriteNotifier(Storage.StorageWriteNotifier storageWriteNotifier) {
        this.mStorageWriteNotifier = storageWriteNotifier;
    }
}
