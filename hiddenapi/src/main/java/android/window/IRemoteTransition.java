package android.window;
/** Stub (AIDL, AOSP main: frameworks/base/core/java/android/window/IRemoteTransition.aidl). */
public interface IRemoteTransition extends android.os.IInterface {
    void startAnimation(android.os.IBinder token, TransitionInfo info, android.view.SurfaceControl.Transaction t,
            IRemoteTransitionFinishedCallback finishCallback) throws android.os.RemoteException;
    void mergeAnimation(android.os.IBinder transition, TransitionInfo info, android.view.SurfaceControl.Transaction t,
            android.os.IBinder mergeTarget, IRemoteTransitionFinishedCallback finishCallback) throws android.os.RemoteException;
    void takeOverAnimation(android.os.IBinder transition, TransitionInfo info, android.view.SurfaceControl.Transaction t,
            IRemoteTransitionFinishedCallback finishCallback, WindowAnimationState[] states) throws android.os.RemoteException;
    void onTransitionConsumed(android.os.IBinder transition, boolean aborted) throws android.os.RemoteException;

    abstract class Stub extends android.os.Binder implements IRemoteTransition {
        public Stub() { throw new RuntimeException("Stub"); }
        public android.os.IBinder asBinder() { throw new RuntimeException("Stub"); }
    }
}
