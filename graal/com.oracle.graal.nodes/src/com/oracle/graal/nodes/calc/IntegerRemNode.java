/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.calc;

import jdk.vm.ci.code.CodeUtil;

import com.oracle.graal.compiler.common.type.IntegerStamp;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

@NodeInfo(shortName = "%")
public class IntegerRemNode extends FixedBinaryNode implements Lowerable, LIRLowerable {
    public static final NodeClass<IntegerRemNode> TYPE = NodeClass.create(IntegerRemNode.class);

    public IntegerRemNode(ValueNode x, ValueNode y) {
        this(TYPE, x, y);
    }

    protected IntegerRemNode(NodeClass<? extends IntegerRemNode> c, ValueNode x, ValueNode y) {
        super(c, IntegerStamp.OPS.getRem().foldStamp(x.stamp(), y.stamp()), x, y);
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(IntegerStamp.OPS.getRem().foldStamp(getX().stamp(), getY().stamp()));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        if (forX.isConstant() && forY.isConstant()) {
            @SuppressWarnings("hiding")
            long y = forY.asJavaConstant().asLong();
            if (y == 0) {
                return this; // this will trap, can not canonicalize
            }
            return ConstantNode.forIntegerStamp(stamp(), forX.asJavaConstant().asLong() % y);
        } else if (forY.isConstant()) {
            long c = forY.asJavaConstant().asLong();
            if (c == 1 || c == -1) {
                return ConstantNode.forIntegerStamp(stamp(), 0);
            } else if (c > 0 && CodeUtil.isPowerOf2(c) && forX.stamp() instanceof IntegerStamp && ((IntegerStamp) forX.stamp()).isPositive()) {
                return new AndNode(forX, ConstantNode.forIntegerStamp(stamp(), c - 1));
            }
        }
        return this;
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().getArithmetic().emitRem(gen.operand(getX()), gen.operand(getY()), gen.state(this)));
    }

    @Override
    public boolean canDeoptimize() {
        return !(getY().stamp() instanceof IntegerStamp) || ((IntegerStamp) getY().stamp()).contains(0);
    }
}
