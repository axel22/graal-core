/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.common.type;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class CheckedJavaType {
    private final ResolvedJavaType type;
    private final boolean exactType;

    private CheckedJavaType(ResolvedJavaType type, boolean exactType) {
        this.type = type;
        this.exactType = exactType;
    }

    public static CheckedJavaType create(Assumptions assumptions, ResolvedJavaType type) {
        ResolvedJavaType exactType = type.asExactType();
        if (exactType == null) {
            Assumptions.AssumptionResult<ResolvedJavaType> leafConcreteSubtype = type.findLeafConcreteSubtype();
            if (leafConcreteSubtype != null && leafConcreteSubtype.canRecordTo(assumptions)) {
                leafConcreteSubtype.recordTo(assumptions);
                exactType = leafConcreteSubtype.getResult();
            }
        }
        if (exactType == null) {
            return new CheckedJavaType(type, false);
        }
        return new CheckedJavaType(exactType, true);
    }

    /**
     * Returns the underlying {@link ResolvedJavaType} object.
     */
    public ResolvedJavaType getType() {
        return type;
    }

    /**
     * Returns the name of this type in internal form.
     *
     * See {@link JavaType#getName()}.
     */
    public String getName() {
        return getType().getName();
    }

    /**
     * Returns {@code true} if and only if this type is the only leaf type.
     */
    public boolean isExact() {
        return exactType;
    }

}
