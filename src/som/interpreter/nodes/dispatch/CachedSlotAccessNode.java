package som.interpreter.nodes.dispatch;

import som.interpreter.objectstorage.FieldAccess.AbstractFieldRead;
import som.interpreter.objectstorage.FieldWriteNode.AbstractFieldWriteNode;
import som.vmobjects.SObject;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;


public abstract class CachedSlotAccessNode extends AbstractDispatchNode {

  @Child protected AbstractFieldRead read;

  public CachedSlotAccessNode(final AbstractFieldRead read) {
    this.read = read;
  }

  public static final class CachedSlotRead extends CachedSlotAccessNode {
    @Child protected AbstractDispatchNode nextInCache;

    private final DispatchGuard           guard;

    public CachedSlotRead(final AbstractFieldRead read,
        final DispatchGuard guard, final AbstractDispatchNode nextInCache) {
      super(read);
      this.guard       = guard;
      this.nextInCache = nextInCache;
      assert nextInCache != null;
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object[] arguments) {
      try {
        if (guard.entryMatches(arguments[0])) {
          return read.read(frame, (SObject) arguments[0]);
        } else {
          return nextInCache.executeDispatch(frame, arguments);
        }
      } catch (InvalidAssumptionException e) {
        CompilerDirectives.transferToInterpreter();
        return replace(nextInCache).
            executeDispatch(frame, arguments);
      }
    }

    @Override
    public int lengthOfDispatchChain() {
      return 1 + nextInCache.lengthOfDispatchChain();
    }
  }

  public static final class CachedSlotWrite extends AbstractDispatchNode {
    @Child protected AbstractDispatchNode   nextInCache;
    @Child protected AbstractFieldWriteNode write;

    private final DispatchGuard             guard;

    public CachedSlotWrite(final AbstractFieldWriteNode write,
        final DispatchGuard guard,
        final AbstractDispatchNode nextInCache) {
      this.write = write;
      this.guard = guard;
      this.nextInCache = nextInCache;
    }

    @Override
    public Object executeDispatch(final VirtualFrame frame,
        final Object[] arguments) {
      try {
        if (guard.entryMatches(arguments[0])) {
          return write.write((SObject) arguments[0], arguments[1]);
        } else {
          return nextInCache.executeDispatch(frame, arguments);
        }
      } catch (InvalidAssumptionException e) {
        CompilerDirectives.transferToInterpreter();
        return replace(nextInCache).executeDispatch(frame, arguments);
      }
    }

    @Override
    public int lengthOfDispatchChain() {
      return 1 + nextInCache.lengthOfDispatchChain();
    }
  }
}
