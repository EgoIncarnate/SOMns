package tools.concurrency;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.oracle.truffle.api.source.SourceSection;

import som.interpreter.actors.Actor;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vm.Activity;
import som.vm.ObjectSystem;
import som.vm.Symbols;
import som.vm.VmSettings;
import som.vmobjects.SInvokable;
import tools.SourceCoordinate;
import tools.debugger.entities.TraceSemantics;
import tools.debugger.entities.TraceSemantics.ActivityDef;
import tools.debugger.entities.TraceSemantics.DynamicScope;
import tools.debugger.entities.TraceSemantics.Implementation;
import tools.debugger.entities.TraceSemantics.PassiveEntity;
import tools.debugger.entities.TraceSemantics.ReceiveOp;
import tools.debugger.entities.TraceSemantics.SendOp;

public class TraceBuffer {

  public static TraceBuffer create() {
    assert VmSettings.ACTOR_TRACING;
    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      return new SyncedTraceBuffer();
    } else {
      return new TraceBuffer();
    }
  }

  private ByteBuffer storage;

  /** Id of the implementation-level thread.
      Thus, not an application-level thread. */
  private long implThreadId;

  /** Id of the last activity that was running on this buffer. */
  private long lastActivityId;

  protected TraceBuffer() { }

  public void init(final ByteBuffer storage, final long implThreadId) {
    this.storage = storage;
    this.implThreadId = implThreadId;
    assert storage.order() == ByteOrder.BIG_ENDIAN;
    recordThreadId();
  }

  public void returnBuffer() {
    ActorExecutionTrace.returnBuffer(storage);
    storage = null;
  }

  public boolean isEmpty() {
    return storage.position() == 0;
  }

  public boolean isFull() {
    return storage.remaining() == 0;
  }

  boolean swapStorage(final Activity current) {
    if (storage == null || storage.position() <= Implementation.IMPL_THREAD.getSize()) {
      return false;
    }
    ActorExecutionTrace.returnBuffer(storage);
    init(ActorExecutionTrace.getEmptyBuffer(), implThreadId);
    recordCurrentActivity(current);
    return true;
  }

  private void recordThreadId() {
    final int start = storage.position();
    assert start == 0;

    storage.put(Implementation.IMPL_THREAD.getId());
    storage.putLong(implThreadId);

    assert storage.position() == start + Implementation.IMPL_THREAD.getSize();
  }

  public void recordCurrentActivity(final Activity current) {
    final int start = storage.position();

    storage.put(Implementation.IMPL_CURRENT_ACTIVITY.getId());
    storage.putLong(current.getId());
    storage.putInt(current.getNextTraceBufferId());

    assert storage.position() == start + Implementation.IMPL_CURRENT_ACTIVITY.getSize();
  }

  protected boolean ensureSufficientSpace(final int requiredSpace,
      final Activity current) {
    if (storage.remaining() < requiredSpace) {
      boolean didSwap = swapStorage(current);
      assert didSwap;
      return didSwap;
    }
    return false;
  }

  public final void recordMainActor(final Actor mainActor,
      final ObjectSystem objectSystem) {
    SourceSection section;

    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      Dispatchable disp = objectSystem.getPlatformClass().
          getDispatchables().get(Symbols.symbolFor("start"));
      SInvokable method = (SInvokable) disp;

      section = method.getInvokable().getSourceSection();
    } else {
      section = null;
    }

    recordActivityCreation(ActivityDef.ACTOR, mainActor.getId(),
        objectSystem.getPlatformClass().getName().getSymbolId(), section, mainActor);
  }

  /** REM: Ensure it is in sync with {@link TraceSemantics#SOURCE_SECTION_SIZE}. */
  private void writeSourceSection(final SourceSection origin) {
    assert !origin.getSource().isInternal() :
      "Need special handling to ensure we see user code reported to trace/debugger";
    storage.putShort(SourceCoordinate.getURI(origin.getSource()).getSymbolId());
    storage.putShort((short) origin.getStartLine());
    storage.putShort((short) origin.getStartColumn());
    storage.putShort((short) origin.getCharLength());
  }

  public void recordActivityCreation(final ActivityDef entity, final long activityId,
      final short symbolId, final SourceSection sourceSection, final Activity current) {
    int requiredSpace = entity.getCreationSize();
    ensureSufficientSpace(requiredSpace, current);

    final int start = storage.position();

    storage.put(entity.getCreationMarker());
    storage.putLong(activityId);
    storage.putShort(symbolId);

    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      writeSourceSection(sourceSection);
    }
    assert storage.position() == start + requiredSpace;
  }

  public void recordActivityCompletion(final ActivityDef entity, final Activity current) {
    int requireSize = entity.getCompletionSize();
    ensureSufficientSpace(requireSize, current);

    final int start = storage.position();
    storage.put(entity.getCompletionMarker());
    assert storage.position() == start + requireSize;
  }

  private void recordEventWithIdAndSource(final byte eventMarker, final int eventSize,
      final long id, final SourceSection section, final Activity current) {
    ensureSufficientSpace(eventSize, current);

    final int start = storage.position();

    storage.put(eventMarker);
    storage.putLong(id);

    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
      writeSourceSection(section);
    }
    assert storage.position() == start + eventSize;
  }

  public void recordScopeStart(final DynamicScope entity, final long scopeId,
      final SourceSection section, final Activity current) {
    recordEventWithIdAndSource(entity.getStartMarker(), entity.getStartSize(),
        scopeId, section, current);
  }

  public void recordScopeEnd(final DynamicScope entity, final Activity current) {
    int requiredSpace = entity.getEndSize();
    ensureSufficientSpace(requiredSpace, current);

    final int start = storage.position();
    storage.put(entity.getEndMarker());

    assert storage.position() == start + requiredSpace;
  }

  public void recordPassiveEntityCreation(final PassiveEntity entity,
      final long entityId, final SourceSection section, final Activity current) {
    recordEventWithIdAndSource(entity.getCreationMarker(),
        entity.getCreationSize(), entityId, section, current);
  }

  public void recordReceiveOperation(final ReceiveOp op, final long sourceId,
      final Activity current) {
    int requiredSpace = op.getSize();
    ensureSufficientSpace(requiredSpace, current);

    final int start = storage.position();
    storage.put(op.getId());
    storage.putLong(sourceId);

    assert storage.position() == start + requiredSpace;
  }

  public void recordSendOperation(final SendOp op, final long entityId,
      final long targetId, final Activity current) {
    int requiredSpace = op.getSize();
    ensureSufficientSpace(requiredSpace, current);

    final int start = storage.position();
    storage.put(op.getId());
    storage.putLong(entityId);
    storage.putLong(targetId);

    assert storage.position() == start + requiredSpace;
  }

  private static class SyncedTraceBuffer extends TraceBuffer {
    protected SyncedTraceBuffer() { super(); }

    @Override
    public synchronized void recordActivityCreation(final ActivityDef entity,
        final long activityId, final short symbolId,
        final SourceSection section, final Activity current) {
      super.recordActivityCreation(entity, activityId, symbolId, section, current);
    }

    @Override
    public synchronized void recordScopeStart(final DynamicScope entity,
        final long scopeId, final SourceSection section, final Activity current) {
      super.recordScopeStart(entity, scopeId, section, current);
    }

    @Override
    public synchronized void recordScopeEnd(final DynamicScope entity,
        final Activity current) {
      super.recordScopeEnd(entity, current);
    }

    @Override
    public synchronized void recordPassiveEntityCreation(final PassiveEntity entity,
        final long entityId, final SourceSection section, final Activity current) {
      super.recordPassiveEntityCreation(entity, entityId, section, current);
    }

    @Override
    public synchronized void recordActivityCompletion(final ActivityDef enttiy,
        final Activity current) {
      super.recordActivityCompletion(enttiy, current);
    }

    @Override
    public synchronized void recordReceiveOperation(final ReceiveOp op,
        final long sourceId, final Activity current) {
      super.recordReceiveOperation(op, sourceId, current);
    }

    @Override
    public synchronized void recordSendOperation(final SendOp op,
        final long entityId, final long targetId, final Activity current) {
      super.recordSendOperation(op, entityId, targetId, current);
    }
  }
}
