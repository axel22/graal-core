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
package com.oracle.graal.nodes;

import jdk.vm.ci.meta.JavaKind;

import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.graph.IterableNodeType;
import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.Canonicalizable;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodes.extended.GuardingNode;
import com.oracle.graal.nodes.spi.LIRLowerable;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;
import com.oracle.graal.nodes.spi.ValueProxy;
import com.oracle.graal.nodes.spi.Virtualizable;
import com.oracle.graal.nodes.spi.VirtualizerTool;
import com.oracle.graal.nodes.virtual.VirtualObjectNode;

/**
 * A node that changes the type of its input, usually narrowing it. For example, a GuardedValueNode
 * is used to keep the nodes depending on guards inside a loop during speculative guard movement.
 *
 * A GuardedValueNode will only go away if its guard is null or {@link StructuredGraph#start()}.
 */
@NodeInfo
public final class GuardedValueNode extends FloatingGuardedNode implements LIRLowerable, Virtualizable, IterableNodeType, Canonicalizable, ValueProxy {

    public static final NodeClass<GuardedValueNode> TYPE = NodeClass.create(GuardedValueNode.class);
    @Input ValueNode object;
    protected final Stamp piStamp;

    public GuardedValueNode(ValueNode object, GuardingNode guard, Stamp stamp) {
        super(TYPE, stamp, guard);
        this.object = object;
        this.piStamp = stamp;
    }

    public GuardedValueNode(ValueNode object, GuardingNode guard) {
        this(object, guard, object.stamp());
    }

    public ValueNode object() {
        return object;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        if (object.getStackKind() != JavaKind.Void && object.getStackKind() != JavaKind.Illegal) {
            generator.setResult(this, generator.operand(object));
        }
    }

    @Override
    public boolean inferStamp() {
        return updateStamp(piStamp.improveWith(object().stamp()));
    }

    @Override
    public void virtualize(VirtualizerTool tool) {
        ValueNode alias = tool.getAlias(object());
        if (alias instanceof VirtualObjectNode) {
            tool.replaceWithVirtual((VirtualObjectNode) alias);
        }
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (getGuard() == null) {
            if (stamp().equals(object().stamp())) {
                return object();
            } else {
                return new PiNode(object(), stamp());
            }
        }
        return this;
    }

    @Override
    public ValueNode getOriginalNode() {
        return object;
    }
}
