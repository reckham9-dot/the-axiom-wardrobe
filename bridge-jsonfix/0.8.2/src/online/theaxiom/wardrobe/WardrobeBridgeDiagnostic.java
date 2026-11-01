package online.theaxiom.wardrobe;
import java.net.http.HttpResponse;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.*;

/** Safe, rate-limited cosmetics POST diagnostic; NEVER logs raw response or credentials. */
public final class WardrobeBridgeDiagnostic {
  private static final Logger LOG = Logger.getLogger("AxiomWardrobe");
  private static final Pattern ERROR = Pattern.compile("\"error\"\\s*:\\s*\"([a-z_]{1,48})\"");
  private static final ConcurrentHashMap<String,AtomicLong> LAST = new ConcurrentHashMap<>();
  private WardrobeBridgeDiagnostic() {}

  private static void warn(String code) {
    long now = System.currentTimeMillis();
    AtomicLong stamp = LAST.computeIfAbsent(code, k -> new AtomicLong(0));
    long old = stamp.get();
    if (now-old >= 20000L && stamp.compareAndSet(old,now))
      LOG.warning("Wardrobe cosmetics bridge diagnostic: " + code + " (no secrets logged)");
  }

  public static Boolean accept(HttpResponse<?> response) {
    int status=response.statusCode();
    if (status==200) return Boolean.TRUE;
    String error="unclassified";
    Object body=response.body();
    if (body instanceof String s) {
      Matcher matcher=ERROR.matcher(s.length()>8192?s.substring(0,8192):s);
      if(matcher.find()) error=matcher.group(1);
    }
    warn("POST http="+status+" error="+error);
    return Boolean.FALSE;
  }

  public static CompletableFuture<Boolean> observe(CompletableFuture<Boolean> future) {
    future.whenComplete((ok,err) -> {
      if(err!=null) {
        Throwable cause=err;
        while(cause instanceof CompletionException && cause.getCause()!=null) cause=cause.getCause();
        String name=cause.getClass().getSimpleName();
        if(!name.matches("[A-Za-z0-9_]{1,64}")) name="UnknownFailure";
        warn("POST transport="+name);
      }
    });
    return future;
  }
}
