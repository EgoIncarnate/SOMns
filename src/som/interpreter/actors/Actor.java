package som.interpreter.actors;

import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import som.VM;
import som.interpreter.objectstorage.ObjectTransitionSafepoint;
import som.primitives.ObjectPrims.IsValue;
import som.vm.Activity;
import som.vm.ActivityThread;
import som.vm.VmSettings;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SArray.STransferArray;
import som.vmobjects.SObject;
import som.vmobjects.SObjectWithClass.SObjectWithoutFields;
import tools.ObjectBuffer;
import tools.concurrency.ActorExecutionTrace;
import tools.concurrency.TracingActors.ReplayActor;
import tools.concurrency.TracingActors.TracingActor;
import tools.debugger.WebDebugger;


/**
 * Represent's a language level actor
 *
 * design goals:
 * - avoid 1-thread per actor
 * - have a low-overhead and safe scheduling system
 * - use an executor or fork/join pool for execution
 * - each actor should only have at max. one active task
 *
 * algorithmic sketch
 *  - enqueue message in actor queue
 *  - execution is done by a special ExecAllMessages task
 *    - this task is submitted to the f/j pool
 *    - once it is executing, it goes to the actor,
 *    - grabs the current mailbox
 *    - and sequentially executes all messages
 */
public class Actor implements Activity {

  public static Actor createActor() {
    if (VmSettings.REPLAY) {
      return new ReplayActor();
    } else if (VmSettings.ACTOR_TRACING) {
      return new TracingActor();
    } else {
      return new Actor();
    }
  }

  /** Used to shift the thread id to the 8 most significant bits. */
  private static final int THREAD_ID_SHIFT = 56;
  private static final int MAILBOX_EXTENSION_SIZE = 8;

  /**
   * Buffer for incoming messages.
   * Optimized for cases where the mailbox contains only one message.
   * Further messages are stored in moreMessages, which is initialized lazily.
   */
  protected EventualMessage firstMessage;
  protected ObjectBuffer<EventualMessage> mailboxExtension;

  protected long firstMessageTimeStamp;
  protected ObjectBuffer<Long> mailboxExtensionTimeStamps;

  /** Flag to indicate whether there is currently a F/J task executing. */
  protected boolean isExecuting;

  /** Is scheduled on the pool, and executes messages to this actor. */
  @CompilationFinal
  protected ExecAllMessages executor;

  // used to collect absolute numbers from the threads
  private static long numCreatedMessages = 0;
  private static long numCreatedActors = 0;
  private static long numCreatedPromises = 0;
  private static long numResolvedPromises = 0;

  private static ArrayList<ActorProcessingThread> threads = new ArrayList<>();

  /**
   * Possible roles for an actor.
   */
  public enum Role {
    SENDER,
    RECEIVER
  }

  protected Actor() {
    isExecuting = false;
    executor = new ExecAllMessages(this);
  }

  public final Object wrapForUse(final Object o, final Actor owner,
      final Map<SAbstractObject, SAbstractObject> transferedObjects) {
    VM.thisMethodNeedsToBeOptimized("This should probably be optimized");

    if (this == owner) {
      return o;
    }

    if (o instanceof SFarReference) {
      if (((SFarReference) o).getActor() == this) {
        return ((SFarReference) o).getValue();
      }
    } else if (o instanceof SPromise) {
      // promises cannot just be wrapped in far references, instead, other actors
      // should get a new promise that is going to be resolved once the original
      // promise gets resolved

      SPromise orgProm = (SPromise) o;
      // assert orgProm.getOwner() == owner; this can be another actor, which initialized a scheduled eventual send by resolving a promise, that's the promise pipelining...
      if (orgProm.getOwner() == this) {
        return orgProm;
      }
      return orgProm.getChainedPromiseFor(this);
    } else if (!IsValue.isObjectValue(o)) {
      // Corresponds to TransferObject.isTransferObject()
      if ((o instanceof SObject && ((SObject) o).getSOMClass().isTransferObject())) {
        return TransferObject.transfer((SObject) o, owner, this,
            transferedObjects);
      } else if (o instanceof STransferArray) {
        return TransferObject.transfer((STransferArray) o, owner, this,
            transferedObjects);
      } else if (o instanceof SObjectWithoutFields && ((SObjectWithoutFields) o).getSOMClass().isTransferObject()) {
        return TransferObject.transfer((SObjectWithoutFields) o, owner, this,
            transferedObjects);
      } else {
        return new SFarReference(owner, o);
      }
    }
    return o;
  }

  /**
   * Send the give message to the actor.
   *
   * This is the main method to be used in this API.
   */
  @TruffleBoundary
  public synchronized void send(final EventualMessage msg) {
    assert msg.getTarget() == this;

    if (firstMessage == null) {
      firstMessage = msg;
      if (VmSettings.MESSAGE_TIMESTAMPS) {
        firstMessageTimeStamp = System.currentTimeMillis();
      }
    } else {
      appendToMailbox(msg);
    }

    if (!isExecuting) {
      isExecuting = true;
      executeOnPool();
    }
  }

  @TruffleBoundary
  protected void appendToMailbox(final EventualMessage msg) {
    if (mailboxExtension == null) {
      mailboxExtension = new ObjectBuffer<>(MAILBOX_EXTENSION_SIZE);
      mailboxExtensionTimeStamps = new ObjectBuffer<>(MAILBOX_EXTENSION_SIZE);
    }
    if (VmSettings.MESSAGE_TIMESTAMPS) {
      mailboxExtensionTimeStamps.append(System.currentTimeMillis());
    }
    mailboxExtension.append(msg);
  }

  protected static void handleBreakPoints(final EventualMessage msg, final WebDebugger dbg) {
    if (VmSettings.TRUFFLE_DEBUGGER_ENABLED && msg.isBreakpoint()) {
      dbg.prepareSteppingUntilNextRootNode();
    }
  }

  /**
   * Is scheduled on the fork/join pool and executes messages for a specific
   * actor.
   */
  public static class ExecAllMessages implements Runnable {
    protected final Actor actor;
    protected EventualMessage firstMessage;
    protected ObjectBuffer<EventualMessage> mailboxExtension;
    protected long baseMessageId;
    protected long firstMessageTimeStamp;
    protected ObjectBuffer<Long> mailboxExtensionTimeStamps;
    protected long[] executionTimeStamps;
    protected int currentMailboxNo;
    protected int size = 0;

    protected ExecAllMessages(final Actor actor) {
      this.actor = actor;
    }

    @Override
    public void run() {
      ObjectTransitionSafepoint.INSTANCE.register();

      ActorProcessingThread t = (ActorProcessingThread) Thread.currentThread();
      WebDebugger dbg = null;
      if (VmSettings.TRUFFLE_DEBUGGER_ENABLED) {
        dbg = VM.getWebDebugger();
        assert dbg != null;
      }

      t.currentlyExecutingActor = actor;

      try {
        while (getCurrentMessagesOrCompleteExecution()) {
          processCurrentMessages(t, dbg);
        }
      } finally {
        ObjectTransitionSafepoint.INSTANCE.unregister();
      }

      t.currentlyExecutingActor = null;
    }

    protected void processCurrentMessages(final ActorProcessingThread currentThread, final WebDebugger dbg) {
      if (VmSettings.ACTOR_TRACING) {
        baseMessageId = currentThread.generateMessageBaseId(size);
        currentThread.currentMessageId = baseMessageId;
      }

      assert (size > 0);
      currentThread.currentMessage = firstMessage;

      handleBreakPoints(firstMessage, dbg);

      firstMessage.execute();

      if (VmSettings.ACTOR_TRACING) {
        currentThread.currentMessageId += 1;
      }

      int i = 0;
      if (size > 1) {
        for (EventualMessage msg : mailboxExtension) {
          currentThread.currentMessage = msg;
          handleBreakPoints(msg, dbg);

          if (VmSettings.MESSAGE_TIMESTAMPS) {
            executionTimeStamps[i] = System.currentTimeMillis();
            i++;
          }

          msg.execute();
          if (VmSettings.ACTOR_TRACING) {
            currentThread.currentMessageId += 1;
          }
        }
      }

      if (VmSettings.ACTOR_TRACING) {
        currentThread.createdMessages += size;
        ActorExecutionTrace.mailboxExecuted(firstMessage, mailboxExtension, baseMessageId, currentMailboxNo, firstMessageTimeStamp, mailboxExtensionTimeStamps, executionTimeStamps, actor);
      }
    }

    private boolean getCurrentMessagesOrCompleteExecution() {
      synchronized (actor) {
        assert actor.isExecuting;
        firstMessage = actor.firstMessage;
        mailboxExtension = actor.mailboxExtension;
        if (actor instanceof TracingActor) {
          currentMailboxNo = ((TracingActor) actor).getAndIncrementMailboxNumber();
        }

        if (firstMessage == null) {
          // complete execution after all messages are processed
          actor.isExecuting = false;
          size = 0;
          return false;
        } else {
          size = 1 + ((mailboxExtension == null) ? 0 : mailboxExtension.size());
        }

        if (VmSettings.MESSAGE_TIMESTAMPS) {
          executionTimeStamps = new long[size];
          firstMessageTimeStamp = actor.firstMessageTimeStamp;
          mailboxExtensionTimeStamps = actor.mailboxExtensionTimeStamps;
        }

        actor.firstMessage = null;
        actor.mailboxExtension = null;
      }

      return true;
    }
  }

  @TruffleBoundary
  protected void executeOnPool() {
    try {
      actorPool.execute(executor);
    } catch (RejectedExecutionException e) {
      throw new ThreadDeath();
    }
  }

  /**
   * @return true, if there are no scheduled submissions,
   *         and no active threads in the pool, false otherwise.
   *         This is only best effort, it does not look at the actor's
   *         message queues.
   */
  public static boolean isPoolIdle() {
    // TODO: this is not working when a thread blocks, then it seems
    //       not to be considered running
    return actorPool.isQuiescent();
  }

  private static final class ActorProcessingThreadFactor implements ForkJoinWorkerThreadFactory {
    @Override
    public ForkJoinWorkerThread newThread(final ForkJoinPool pool) {
      ActorProcessingThread t = new ActorProcessingThread(pool);
      threads.add(t);
      return t;
    }
  }

  public static final class ActorProcessingThread extends ForkJoinWorkerThread implements ActivityThread {
    public EventualMessage currentMessage;
    private static AtomicInteger threadIdGen = new AtomicInteger(0);
    protected Actor currentlyExecutingActor;
    protected final long threadId;
    protected long nextActorId = 1;
    protected long nextMessageId;
    protected long nextPromiseId;
    public long createdMessages;
    public long currentMessageId;
    protected ByteBuffer tracingDataBuffer;
    public long resolvedPromises;

    protected ActorProcessingThread(final ForkJoinPool pool) {
      super(pool);
      threadId = threadIdGen.getAndIncrement();
      if (VmSettings.ACTOR_TRACING) {
        ActorExecutionTrace.swapBuffer(this);
        nextActorId = (threadId << THREAD_ID_SHIFT) + 1;
        nextMessageId = (threadId << THREAD_ID_SHIFT);
        nextPromiseId = (threadId << THREAD_ID_SHIFT);
      }
    }

    @Override
    public Activity getActivity() {
      return currentMessage.getTarget();
    }

    public long generateActorId() {
      return nextActorId++;
    }

    public long generateMessageBaseId(final int numMessages) {
      long result = nextMessageId;
      nextMessageId += numMessages;
      return result;
    }

    protected long generatePromiseId() {
      return nextPromiseId++;
    }

    public ByteBuffer getThreadLocalBuffer() {
      return tracingDataBuffer;
    }

    public void setThreadLocalBuffer(final ByteBuffer threadLocalBuffer) {
      this.tracingDataBuffer = threadLocalBuffer;
    }

    public long getCurrentMessageId() {
      return currentMessageId;
    }

    @Override
    protected void onTermination(final Throwable exception) {
      if (VmSettings.ACTOR_TRACING) {
        long createdActors = nextActorId - 1 - (threadId << THREAD_ID_SHIFT);
        long createdPromises = nextPromiseId - (threadId << THREAD_ID_SHIFT);

        ActorExecutionTrace.returnBuffer(this.tracingDataBuffer);
        this.tracingDataBuffer = null;
        VM.printConcurrencyEntitiesReport("[Thread " + threadId + "]\tA#" + createdActors + "\t\tM#" + createdMessages + "\t\tP#" + createdPromises);
        numCreatedActors += createdActors;
        numCreatedMessages += createdMessages;
        numCreatedPromises += createdPromises;
        numResolvedPromises += resolvedPromises;
      }
      threads.remove(this);
      super.onTermination(exception);
    }
  }

  /**
   * In case an actor processing thread terminates, provide some info.
   */
  public static final class UncaughtExceptions implements UncaughtExceptionHandler {
    @Override
    public void uncaughtException(final Thread t, final Throwable e) {
      if (e instanceof ThreadDeath) {
        // Ignore those, we already signaled an error
        return;
      }
      ActorProcessingThread thread = (ActorProcessingThread) t;
      VM.errorPrintln("Processing of eventual message failed for actor: "
          + thread.currentlyExecutingActor.toString());
      e.printStackTrace();
    }
  }

  private static final ForkJoinPool actorPool = new ForkJoinPool(
      VmSettings.NUM_THREADS, new ActorProcessingThreadFactor(),
      new UncaughtExceptions(), true);

  public static final void shutDownActorPool() {
      actorPool.shutdown();
      try {
        actorPool.awaitTermination(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      if (VmSettings.ACTOR_TRACING) {
        VM.printConcurrencyEntitiesReport("[Total]\tA#" + numCreatedActors + "\t\tM#" + numCreatedMessages + "\t\tP#" + numCreatedPromises);
        VM.printConcurrencyEntitiesReport("[Unresolved] " + (numCreatedPromises - numResolvedPromises));
      }
  }

  public static final void forceSwapBuffers() {
    for (ActorProcessingThread t: threads) {
      ActorExecutionTrace.swapBuffer(t);
    }
  }

  @Override
  public String getName() {
    return toString();
  }

  @Override
  public String toString() {
    return "Actor";
  }
}
