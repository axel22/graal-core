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
