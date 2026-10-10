package io.ahad.connectorhub.core;
import java.util.Set;
public record Identity(String keyId,String tenant,String role,Set<String> providers) {
    public boolean admin() {
        return role.equals("ADMIN");
    }
    public boolean designer() {
        return admin()||role.equals("DESIGNER");
    }
    public boolean auditor() {
        return designer()||role.equals("AUDITOR");
    }
    public boolean canInvoke(String id) {
        return (admin()||role.equals("DESIGNER")||role.equals("INVOKER"))&&(providers.isEmpty()||providers.contains(id));
    }
    public void requireDesigner() {
        if(!designer())throw new HubException("FORBIDDEN",403);
    }
    public void requireAdmin() {
        if(!admin())throw new HubException("FORBIDDEN",403);
    }
}
