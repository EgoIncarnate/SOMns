/**
 * Copyright (c) 2013 Stefan Marr, stefan.marr@vub.ac.be
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
package som.interpreter;

import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.source.SourceSection;

import som.compiler.MethodBuilder;
import som.interpreter.LexicalScope.MethodScope;
import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.SOMNode;
import som.vmobjects.SInvokable;


public final class Method extends Invokable {

  private final MethodScope methodScope;
  private final SourceSection[] definition;
  private final boolean block;

  public Method(final String name, final SourceSection sourceSection,
                final SourceSection[] definition,
                final ExpressionNode expressions,
                final MethodScope methodScope,
                final ExpressionNode uninitialized, final boolean block) {
    super(name, sourceSection, methodScope.getFrameDescriptor(),
        expressions, uninitialized);
    this.definition = definition;
    this.block = block;
    this.methodScope = methodScope;
    assert methodScope.isFinalized();
    expressions.markAsRootExpression();
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) { return true; }
    if (!(o instanceof Method)) { return false; }

    Method m = (Method) o;

    if (name.equals(m.name) && getSourceSection() == m.getSourceSection()) {
      return true;
    }

    assert !getSourceSection().equals(m.getSourceSection()) : "If that triggers, something with the source sections is wrong.";
    return false;
  }

  public SourceSection[] getDefinition() {
    return definition;
  }

  @Override
  public String toString() {
    return "Method " + getName() + "\t@" + Integer.toHexString(hashCode());
  }

  /**
   * @param splitScope is the scope for the block, which has already been split.
   */
  @Override
  public Invokable cloneWithNewLexicalContext(final MethodScope splitScope) {
    assert methodScope != splitScope;
    assert splitScope.isFinalized();

    ExpressionNode  inlinedBody = SplitterForLexicallyEmbeddedCode.doInline(
        uninitializedBody, splitScope);
    Method clone = new Method(name, getSourceSection(), definition, inlinedBody,
        splitScope, uninitializedBody, block);
    splitScope.setMethod(clone);
    return clone;
  }

  @Override
  public ExpressionNode inline(final MethodBuilder builder, final SInvokable outer) {
    builder.mergeIntoScope(methodScope, outer);
    return InlinerForLexicallyEmbeddedMethods.doInline(uninitializedBody, builder);
  }

  public Invokable cloneAndAdaptToEmbeddedOuterContext(
      final InlinerForLexicallyEmbeddedMethods inliner) {
    MethodScope currentAdaptedScope = inliner.getScope(this);

    ExpressionNode adaptedBody = InlinerAdaptToEmbeddedOuterContext.doInline(
        uninitializedBody, inliner, currentAdaptedScope);
    ExpressionNode uninitAdaptedBody = NodeUtil.cloneNode(adaptedBody);

    Method clone = new Method(name, getSourceSection(), definition, adaptedBody,
        currentAdaptedScope, uninitAdaptedBody, block);
    currentAdaptedScope.setMethod(clone);
    return clone;
  }

  public Invokable cloneAndAdaptToSomeOuterContextBeingEmbedded(
      final InlinerAdaptToEmbeddedOuterContext inliner) {
    MethodScope currentAdaptedScope = inliner.getScope(this);
    ExpressionNode adaptedBody = InlinerAdaptToEmbeddedOuterContext.doInline(
        uninitializedBody, inliner, currentAdaptedScope);
    ExpressionNode uninitAdaptedBody = NodeUtil.cloneNode(adaptedBody);

    Method clone = new Method(name, getSourceSection(), definition,
        adaptedBody, currentAdaptedScope, uninitAdaptedBody, block);
    currentAdaptedScope.setMethod(clone);
    return clone;
  }

  @Override
  public void propagateLoopCountThroughoutMethodScope(final long count) {
    assert count >= 0;
    methodScope.propagateLoopCountThroughoutMethodScope(count);
    LoopNode.reportLoopCount(expressionOrSequence,
        (count > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) count);
  }

  @Override
  public Node deepCopy() {
    return cloneWithNewLexicalContext(methodScope.getOuterMethodScopeOrNull());
  }

  public boolean isBlock() {
    return block;
  }

  public SourceSection getRootNodeSource() {
    ExpressionNode root = SOMNode.unwrapIfNecessary(expressionOrSequence);
    assert root.isMarkedAsRootExpression();

    return root.getSourceSection();
  }

  public MethodScope getLexicalScope() {
    return methodScope;
  }
}
