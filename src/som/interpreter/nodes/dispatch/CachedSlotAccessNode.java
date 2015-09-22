package som.interpreter.nodes.dispatch;

import som.interpreter.nodes.SlotAccessNode;
import som.interpreter.objectstorage.FieldAccessorNode.AbstractWriteFieldNode;
import som.interpreter.objectstorage.ObjectLayout;
import som.vmobjects.SObject;

import com.oracle.truffle.api.frame.VirtualFrame;


public class CachedSlotAccessNode extends AbstractDispatchNode {

  @Child protected SlotAccessNode access;

  public CachedSlotAccessNode(final SlotAccessNode access) {
    this.access = access;
  }

  @Override
  public Object executeDispatch(final VirtualFrame frame,
      final Object[] arguments) {
    assert arguments[0] instanceof SObject;
    SObject rcvr = (SObject) arguments[0];
    return access.doRead(frame, rcvr);
  }

  public static final class CheckedCachedSlotAccessNode extends CachedSlotAccessNode {
    @Child protected AbstractDispatchNode nextInCache;

    private final ObjectLayout layout;

    public CheckedCachedSlotAccessNode(final ObjectLayout layout,
      final SlotAccessNode access,
      final AbstractDispatchNode nextInCache) {
      super(access);
      this.nextInCache = nextInCache;
      this.layout = layout;
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object[] arguments) {
      SObject rcvr = (SObject) arguments[0];

      if (rcvr.getObjectLayout() == layout) {
        return access.doRead(frame, rcvr);
      } else {
        return nextInCache.executeDispatch(frame, arguments);
      }
    }

    @Override
    public int lengthOfDispatchChain() {
      return 1 + nextInCache.lengthOfDispatchChain();
    }
  }

  @Override
  public int lengthOfDispatchChain() { return 1; }

  public static class CachedSlotWriteNode extends AbstractDispatchNode {

    @Child protected AbstractWriteFieldNode write;

    public CachedSlotWriteNode(final AbstractWriteFieldNode write) {
      this.write = write;
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object[] arguments) {
      assert arguments[0] instanceof SObject;
      SObject rcvr = (SObject) arguments[0];
      return write.write(rcvr, arguments[1]);
    }

    @Override
    public int lengthOfDispatchChain() {
      return 1;
    }
  }

  public static final class CheckedCachedSlotWriteNode extends CachedSlotWriteNode {
    private final ObjectLayout layout;
    @Child protected AbstractDispatchNode nextInCache;

    public CheckedCachedSlotWriteNode(final ObjectLayout layout,
        final AbstractWriteFieldNode write,
        final AbstractDispatchNode nextInCache) {
      super(write);
      this.layout = layout;
      this.nextInCache = nextInCache;
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object[] arguments) {
      SObject rcvr = (SObject) arguments[0];

      if (rcvr.getObjectLayout() == layout) {
        return write.write(rcvr, arguments[1]);
      } else {
        return nextInCache.executeDispatch(frame, arguments);
      }
    }

    @Override
    public int lengthOfDispatchChain() {
      return 1 + nextInCache.lengthOfDispatchChain();
    }
  }
}
