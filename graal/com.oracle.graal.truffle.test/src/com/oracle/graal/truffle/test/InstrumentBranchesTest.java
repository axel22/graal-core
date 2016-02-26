/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.truffle.test;

import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.test.GraalCompilerTest;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.StoreIndexedNode;
import com.oracle.graal.options.OptionValue;
import com.oracle.graal.truffle.*;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.TruffleRuntimeAccess;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.services.Services;
import org.junit.Assert;
import org.junit.Test;

public class InstrumentBranchesTest extends GraalCompilerTest {

    @Test
    public void test1() throws Exception {
        testHelper("test1", new TestNode1());
    }

    static class TestNode1 extends TestLanguageNode {
        private Object testObject = new Object();

        @Override
        public Object execute(VirtualFrame vFrame) {
            int x = 0;
            while (x < 5) {
                if (x < 3) {
                    x += 1;
                } else {
                    x += testObject.toString().length();
                }
            }
            return testObject;
        }
    }

    abstract static class TestLanguageNode extends com.oracle.truffle.api.nodes.Node {
        public abstract Object execute(VirtualFrame vFrame);
    }

    static class TestRootNode extends RootNode {
        private final String name;
        private final TestLanguageNode body;

        /**
         * This constructor emulates the global machinery that applies registered probers to every
         * newly created AST. Global registry is not used, since that would interfere with other
         * tests run in the same environment.
         */
        TestRootNode(String name, TestLanguageNode body) {
            super(InstrumentationPETestLanguage.class, null, null);
            this.name = name;
            this.body = body;
        }

        @Override
        public Object execute(VirtualFrame vFrame) {
            return body.execute(vFrame);
        }

        @Override
        public boolean isCloningAllowed() {
            return true;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private void checkInstrumented(Node node, boolean isTrue) {
        BeginNode beginNode = (BeginNode) node;
        StoreIndexedNode store = (StoreIndexedNode) beginNode.successors().first();
        Stamp stamp = store.inputs().filter(ConstantNode.class).first().stamp();
        Assert.assertTrue(stamp.javaType(getMetaAccess()).getElementalType().getJavaKind().equals(JavaKind.Boolean));
    }

    @SuppressWarnings("try")
    private void testHelper(String name, TestLanguageNode testNode) throws Exception {
        RootNode rootNode = new TestRootNode(name, testNode);
        try (Debug.Scope s = Debug.scope("InstrumentBranchesTest");
             OptionValue.OverrideScope o1 = OptionValue.override(TruffleCompilerOptions.InstrumentBranches, true);
             OptionValue.OverrideScope o2 = OptionValue.override(TruffleCompilerOptions.InstrumentBranchesFilter, ".*callRoot.*")
        ) {
            TruffleRuntime runtime = Services.loadSingle(TruffleRuntimeAccess.class, true).getRuntime();
            TruffleCompiler compiler = DefaultTruffleCompiler.create((GraalTruffleRuntime) runtime);
            final OptimizedCallTarget callTarget = (OptimizedCallTarget) runtime.createCallTarget(rootNode);
            StructuredGraph graph = compiler.getPartialEvaluator().createGraph(callTarget, StructuredGraph.AllowAssumptions.YES);

            for (IfNode node : graph.getNodes().filter(IfNode.class)) {
                if (node.predecessor() instanceof InfopointNode) {
                    checkInstrumented(node.trueSuccessor(), true);
                    checkInstrumented(node.falseSuccessor(), false);
                }
            }
        } catch (Throwable e) {
            Debug.handle(e);
        }
    }

}
