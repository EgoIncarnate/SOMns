package som.interpreter.actors;

import som.interpreter.actors.SPromise.SResolver;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;


public abstract class ReceivedRootNode extends RootNode {

  @Child protected ResolvePromiseNode resolve;

  protected ReceivedRootNode(final Class<? extends TruffleLanguage<?>> language,
      final SourceSection sourceSection, final FrameDescriptor frameDescriptor) {
    super(language, sourceSection, frameDescriptor);
  }

  protected final void resolvePromise(final SResolver resolver, final Object result) {
    if (resolve == null) {
      if (resolver == null) {
        this.resolve = insert(new NullResolver());
      } else {
        this.resolve = insert(ResolvePromiseNodeFactory.create(null, null));
      }
    }

    resolve.executeEvaluated(resolver, result);
  }

  public final class NullResolver extends ResolvePromiseNode {

    @Override
    public Object executeEvaluated(final SResolver receiver, final Object argument) {
      assert receiver == null;
      return null;
    }

    @Override
    public Object executeEvaluated(final VirtualFrame frame, final Object receiver,
        final Object argument) {
      return null;
    }

    @Override
    public Object executeGeneric(final VirtualFrame frame) {
      return null;
    }
  }
}
