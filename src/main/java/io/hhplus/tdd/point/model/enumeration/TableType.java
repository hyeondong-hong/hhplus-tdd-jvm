package io.hhplus.tdd.point.model.enumeration;

public enum TableType {
    USER_POINT, POINT_HISTORY;

    public String makeScope(Long id) {
        StringBuilder scope = new StringBuilder();
        if (id != null) {
            scope.append(id).append("_");
        }
        return scope.append(this.name()).toString();
    }

    public String makeScope() {
        return makeScope(null);
    }
}
