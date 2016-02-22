package som.interpreter.nodes.dispatch;

import som.instrumentation.DispatchNodeWrapper;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import dym.Tagging.Tagged;


@Instrumentable(factory = DispatchNodeWrapper.class)
public abstract class AbstractDispatchNode
    extends Node implements DispatchChain, Tagged {
  public static final int INLINE_CACHE_SIZE = 6;

  @CompilationFinal private SourceSection sourceSection;

  protected AbstractDispatchNode(final SourceSection source) {
    super();
    assert source != null;
    this.sourceSection = source;
  }

  /**
   * For wrapped nodes only.
   */
  protected AbstractDispatchNode(final AbstractDispatchNode wrappedNode) {
    super();
    this.sourceSection = null;
  }

  @Override
  public SourceSection getSourceSection() {
    return sourceSection;
  }

  @Override
  public void updateTags(final SourceSection sourceSection) {
    this.sourceSection = sourceSection;
  }

  public abstract Object executeDispatch(
      final VirtualFrame frame, final Object[] arguments);
}
