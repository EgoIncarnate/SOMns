/**
 * Copyright (c) 2013 Stefan Marr,   stefan.marr@vub.ac.be
 * Copyright (c) 2009 Michael Haupt, michael.haupt@hpi.uni-potsdam.de
 * Software Architecture Group, Hasso Plattner Institute, Potsdam, Germany
 * http://www.hpi.uni-potsdam.de/swa/
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package som.vmobjects;

import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.compiler.AccessModifier;
import som.interpreter.Invokable;
import som.interpreter.nodes.dispatch.AbstractDispatchNode;
import som.interpreter.nodes.dispatch.AbstractDispatchNode.AbstractCachedDispatchNode;
import som.interpreter.nodes.dispatch.CachedDispatchSObjectCheckNode;
import som.interpreter.nodes.dispatch.CachedDispatchSimpleCheckNode;
import som.interpreter.nodes.dispatch.CachedDispatchSimpleCheckNode.CachedDispatchFalseCheckNode;
import som.interpreter.nodes.dispatch.CachedDispatchSimpleCheckNode.CachedDispatchTrueCheckNode;
import som.interpreter.nodes.dispatch.Dispatchable;
import som.vm.constants.Classes;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.source.SourceSection;

public abstract class SInvokable extends SAbstractObject implements Dispatchable {

  private final AccessModifier     accessModifier;
  private final SSymbol            category;
  private final Invokable          invokable;
  private final RootCallTarget     callTarget;
  private final SSymbol            signature;
  @CompilationFinal private SClass holder;

  public SInvokable(final SAbstractObject enclosing, final SSymbol signature,
      final AccessModifier accessModifier,
      final SSymbol category, final Invokable invokable) {
    super(enclosing);
    this.signature = signature;
    this.accessModifier = accessModifier;
    this.category = category;

    this.invokable   = invokable;
    this.callTarget  = invokable.createCallTarget();
  }

  public static final class SMethod extends SInvokable {
    private final SMethod[] embeddedBlocks;

    public SMethod(final SAbstractObject enclosing, final SSymbol signature,
        final AccessModifier accessModifier,
        final SSymbol category, final Invokable invokable,
        final SMethod[] embeddedBlocks) {
      super(enclosing, signature, accessModifier, category, invokable);
      this.embeddedBlocks = embeddedBlocks;
    }

    public SMethod[] getEmbeddedBlocks() {
      return embeddedBlocks;
    }

    @Override
    public void setHolder(final SClass value) {
      super.setHolder(value);
      for (SMethod m : embeddedBlocks) {
        m.setHolder(value);
      }
    }

    @Override
    public SClass getSOMClass() {
      assert Classes.methodClass != null;
      return Classes.methodClass;
    }
  }

  public static final class SPrimitive extends SInvokable {
    public SPrimitive(final SAbstractObject enclosing, final SSymbol signature,
        final Invokable invokable) {
      super(enclosing, signature, AccessModifier.PRIVATE, null, invokable);
    }

    @Override
    public SClass getSOMClass() {
      assert Classes.primitiveClass != null;
      return Classes.primitiveClass;
    }
  }

  @Override
  public final RootCallTarget getCallTargetIfAvailable() {
    return callTarget;
  }

  public final Invokable getInvokable() {
    return invokable;
  }

  public final SSymbol getSignature() {
    return signature;
  }

  public final SClass getHolder() {
    return holder;
  }

  public void setHolder(final SClass value) {
    transferToInterpreterAndInvalidate("SMethod.setHolder");
    holder = value;
  }

  public final int getNumberOfArguments() {
    return getSignature().getNumberOfSignatureArguments();
  }

  @Override
  public final Object invoke(final Object... arguments) {
    return callTarget.call(arguments);
  }

  public final Object invoke(final VirtualFrame frame, final IndirectCallNode node, final Object... arguments) {
    return node.call(frame, callTarget, arguments);
  }

  @Override
  public final String toString() {
    // TODO: fixme: remove special case if possible, I think it indicates a bug
    if (holder == null) {
      return "Method(nil>>" + getSignature().toString() + ")";
    }

    return "Method(" + getHolder().getName().getString() + ">>" + getSignature().toString() + ")";
  }

  @Override
  public final AccessModifier getAccessModifier() {
    return accessModifier;
  }

  public SSymbol getCategory() {
    return category;
  }

  public final SourceSection getSourceSection() {
    return invokable.getSourceSection();
  }

  @Override
  public final AbstractCachedDispatchNode getDispatchNode(final Object rcvr,
      final Object rcvrClass, final AbstractDispatchNode next) {
    if (rcvrClass instanceof SClass) {
      return new CachedDispatchSObjectCheckNode(
          (SClass) rcvrClass, callTarget, next);
    } else if (rcvr == Boolean.TRUE) {
      return new CachedDispatchTrueCheckNode(callTarget, next);
    } else if (rcvr == Boolean.FALSE) {
      return new CachedDispatchFalseCheckNode(callTarget, next);
    } else {
      assert rcvrClass instanceof Class;
      return new CachedDispatchSimpleCheckNode((Class<?>) rcvrClass, callTarget, next);
    }
  }
}
