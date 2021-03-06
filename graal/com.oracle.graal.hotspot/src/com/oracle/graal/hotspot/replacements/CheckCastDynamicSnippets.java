/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.replacements;

import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.SECONDARY_SUPER_CACHE_LOCATION;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.loadHubIntrinsic;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.verifyOop;
import static com.oracle.graal.hotspot.replacements.TypeCheckSnippetUtils.checkUnknownSubType;
import static com.oracle.graal.hotspot.replacements.TypeCheckSnippetUtils.isNull;
import static com.oracle.graal.nodes.PiNode.piCast;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.probability;
import static com.oracle.graal.replacements.SnippetTemplate.DEFAULT_REPLACER;
import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateReprofile;
import static jdk.vm.ci.meta.DeoptimizationReason.ClassCastException;
import jdk.vm.ci.code.TargetDescription;

import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.hotspot.nodes.SnippetAnchorNode;
import com.oracle.graal.hotspot.word.KlassPointer;
import com.oracle.graal.nodes.DeoptimizeNode;
import com.oracle.graal.nodes.PiNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.extended.GuardingNode;
import com.oracle.graal.nodes.java.CheckCastDynamicNode;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.replacements.Snippet;
import com.oracle.graal.replacements.SnippetTemplate;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.replacements.Snippets;

/**
 * Snippet used for lowering {@link CheckCastDynamicNode}.
 */
public class CheckCastDynamicSnippets implements Snippets {

    @Snippet
    public static Object checkcastDynamic(KlassPointer hub, Object object) {
        if (probability(NOT_FREQUENT_PROBABILITY, object == null)) {
            isNull.inc();
        } else {
            GuardingNode anchorNode = SnippetAnchorNode.anchor();
            KlassPointer objectHub = loadHubIntrinsic(PiNode.piCastNonNull(object, anchorNode));
            if (!checkUnknownSubType(hub, objectHub)) {
                DeoptimizeNode.deopt(InvalidateReprofile, ClassCastException);
            }
        }
        GuardingNode anchorNode = SnippetAnchorNode.anchor();
        return piCast(verifyOop(object), StampFactory.forNodeIntrinsic(), anchorNode);
    }

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo dynamic = snippet(CheckCastDynamicSnippets.class, "checkcastDynamic", SECONDARY_SUPER_CACHE_LOCATION);

        public Templates(HotSpotProviders providers, TargetDescription target) {
            super(providers, providers.getSnippetReflection(), target);
        }

        public void lower(CheckCastDynamicNode checkcast, LoweringTool tool) {
            StructuredGraph graph = checkcast.graph();
            ValueNode object = checkcast.object();

            Arguments args = new Arguments(dynamic, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("hub", checkcast.hub());
            args.add("object", object);

            SnippetTemplate template = template(args);
            Debug.log("Lowering dynamic checkcast in %s: node=%s, template=%s, arguments=%s", graph, checkcast, template, args);
            template.instantiate(providers.getMetaAccess(), checkcast, DEFAULT_REPLACER, args);
        }
    }
}
