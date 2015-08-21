package som.interpreter.nodes.dispatch;

import som.vmobjects.SObjectWithClass;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.utilities.BranchProfile;


public final class SObjectCheckDispatchNode extends AbstractDispatchNode {

  @Child private AbstractDispatchNode nextInCache;
  @Child private AbstractDispatchNode uninitializedDispatch;

  private final BranchProfile uninitialized;

  public SObjectCheckDispatchNode(final AbstractDispatchNode nextInCache,
      final AbstractDispatchNode uninitializedDispatch) {
    this.nextInCache           = nextInCache;
    this.uninitializedDispatch = uninitializedDispatch;
    this.uninitialized         = BranchProfile.create();
  }

  @Override
  public Object executeDispatch(
      final VirtualFrame frame, final Object[] arguments) {
    Object rcvr = arguments[0];
    if (rcvr instanceof SObjectWithClass) {
      return nextInCache.executeDispatch(frame, arguments);
    } else {
      uninitialized.enter();
      return uninitializedDispatch.executeDispatch(frame, arguments);
    }
  }

  @Override
  public int lengthOfDispatchChain() {
    return nextInCache.lengthOfDispatchChain();
  }
}
