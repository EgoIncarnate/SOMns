package som.interpreter.nodes.dispatch;

import som.interpreter.nodes.ISuperReadNode;
import som.vmobjects.SInvokable;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;

/**
 * Super sends are special, they lead to a lexically defined receiver class.
 * So, it's always the cached receiver.
 */
public final class SuperDispatchNode extends AbstractDispatchNode {

  public static SuperDispatchNode create(final SSymbol selector,
      final ISuperReadNode superNode) {
    SInvokable method = superNode.getSuperClass().lookupInvokable(selector);

    if (method == null) {
      throw new RuntimeException("Currently #dnu with super sent is not yet implemented. ");
    }
    DirectCallNode superMethodNode = Truffle.getRuntime().createDirectCallNode(
        method.getCallTarget());
    return new SuperDispatchNode(superMethodNode);
  }

  @Child private DirectCallNode cachedSuperMethod;

  private SuperDispatchNode(final DirectCallNode superMethod) {
    this.cachedSuperMethod = superMethod;
  }

  @Override
  public Object executeDispatch(
      final VirtualFrame frame, final Object[] arguments) {
    return cachedSuperMethod.call(frame, arguments);
  }
}