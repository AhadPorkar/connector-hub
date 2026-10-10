package io.ahad.connectorhub.connectors;
import io.ahad.connectorhub.core.*;
import java.net.*;
import java.util.*;
/** Exact operator-owned origin allowlist. DNS checks are defense in depth, not an egress firewall. */
public final class NetworkPolicy {
    private final Set<String> origins;
    private final boolean privateAllowed;
    private final List<String> jdbcPrefixes;
    public NetworkPolicy(Collection<String> origins,boolean privateAllowed,Collection<String> jdbcPrefixes) {
        this.origins=new HashSet<>();
        for(String s:origins)if(!s.isBlank())this.origins.add(origin(shape(s.trim())));
        this.privateAllowed=privateAllowed;
        this.jdbcPrefixes=List.copyOf(jdbcPrefixes);
    }
    public static URI shape(String address) {
        try {
            URI u=URI.create(address);
            if(!Set.of("http","https","redis","rediss","mongodb").contains(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getFragment()!=null||address.chars().anyMatch(c->c<32||c==127)||u.getPort()<-1||u.getPort()>65535||u.getPort()==0)throw HubException.invalid();
            return u;
        }
        catch(IllegalArgumentException e) {
            throw new HubException("DESTINATION_BLOCKED",403);
        }
    }
    public static String origin(URI uri) {
        int port=uri.getPort();
        if(port<0)port=switch(uri.getScheme()) {
            case "http"->80;
            case "https"->443;
            case "redis","rediss"->6379;
            case "mongodb"->27017;
            default->throw HubException.invalid();
        }
        ;
        return uri.getScheme()+"://"+uri.getHost().toLowerCase(Locale.ROOT)+":"+port;
    }
    public URI approve(String address) {
        URI uri=shape(address);
        if(!origins.contains(origin(uri)))throw new HubException("DESTINATION_BLOCKED",403);
        try {
            for(InetAddress ip:InetAddress.getAllByName(uri.getHost())) {
                if(ip.isAnyLocalAddress()||ip.isLinkLocalAddress()||ip.isMulticastAddress())throw new HubException("DESTINATION_BLOCKED",403);
                boolean privateIp=ip.isLoopbackAddress()||ip.isSiteLocalAddress()||(ip instanceof Inet6Address&&(ip.getAddress()[0]&0xfe)==0xfc);
                if(privateIp&&!privateAllowed)throw new HubException("DESTINATION_BLOCKED",403);
            }
        }
        catch(UnknownHostException e) {
            throw new HubException("DESTINATION_UNRESOLVED",502,true);
        }
        return uri;
    }
    public void approveJdbc(String url) {
        if(url==null||url.length()>2_000||jdbcPrefixes.stream().noneMatch(p->!p.isBlank()&&url.startsWith(p)))throw new HubException("DESTINATION_BLOCKED",403);
        String lower=url.toLowerCase(Locale.ROOT);
        for(String forbidden:List.of("init=","autodeserialize=","socketfactory=","sslfactory=","allowloadlocalinfile=true","allowurlinlocalinfile=true","password=",";runscript","\\n","\\r"))if(lower.contains(forbidden))throw new HubException("DESTINATION_BLOCKED",403);
        if(url.chars().anyMatch(c->c<32))throw new HubException("DESTINATION_BLOCKED",403);
    }
}
