package dym.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;

import dym.profiles.BranchProfile;


public class ControlFlowProfileNode extends ExecutionEventNode {
  protected final BranchProfile profile;

  public ControlFlowProfileNode(final BranchProfile profile) {
    this.profile = profile;
  }

  @Override
  protected void onReturnValue(final VirtualFrame frame, final Object result) {
    profile.profile((boolean) result);
  }
}
