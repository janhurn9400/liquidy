package android.window;
/** Stub (AIDL, AOSP main). */
public interface IRemoteTransitionFinishedCallback extends android.os.IInterface {
    void onTransitionFinished(WindowContainerTransaction wct, android.view.SurfaceControl.Transaction sct) throws android.os.RemoteException;
}
