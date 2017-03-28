package tools.concurrency;


public abstract class Tags {


  /** Marks the eventual message send operator. */
  public final class EventualMessageSend extends Tags {
    private EventualMessageSend() { }
  }

  /** Marks a read operation on a channel. */
  public final class ChannelRead extends Tags {
    private ChannelRead() { }
  }

  /** Marks a write operation on a channel. */
  public final class ChannelWrite extends Tags {
    private ChannelWrite() { }
  }

  /** Marks an expression that can be target of a breakpoint. */
  public final class ExpressionBreakpoint extends Tags {
    private ExpressionBreakpoint() { }
  }

  /** Marks the createPromisePair primitive. */
  public final class CreatePromisePair extends Tags {
    private CreatePromisePair() { }
  }

  /** Marks the whenResolved primitive. */
  public final class WhenResolved extends Tags {
    private WhenResolved() { }
  }

  /** Marks the whenResolvedOnError primitive. */
  public final class WhenResolvedOnError extends Tags {
    private WhenResolvedOnError() { }
  }

  /** Marks the onError primitive. */
  public final class OnError extends Tags {
    private OnError() { }
  }
}
