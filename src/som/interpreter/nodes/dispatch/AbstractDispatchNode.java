package som.interpreter.nodes.dispatch;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;


public abstract class AbstractDispatchNode
    extends Node implements DispatchChain {
  public static final int INLINE_CACHE_SIZE = 6;

  protected AbstractDispatchNode(final SourceSection source) {
    super(source);
    assert source != null;
  }

  public abstract Object executeDispatch(
      final VirtualFrame frame, final Object[] arguments);
}
