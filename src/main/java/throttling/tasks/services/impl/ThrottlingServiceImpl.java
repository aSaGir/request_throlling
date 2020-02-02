package throttling.tasks.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import throttling.tasks.services.SlaService;
import throttling.tasks.services.ThrottlingService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;


@Slf4j
@Service
@RequiredArgsConstructor
public class ThrottlingServiceImpl implements ThrottlingService {

    private final SlaService slaService;
    private final TimeService timeService;

    private Map<String, CompletableFuture<SlaService.SLA>> users = new ConcurrentHashMap<>();
    private Map<String, SlaService.SLA> caches = new ConcurrentHashMap<>();
    private Map<String, Map<Long, AtomicInteger>> requests = new HashMap<>();
    private Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    private static int GUEST_RPS = 10;
    private static String GUEST_USER = "guest";
    private static SlaService.SLA GUEST_SLA = new SlaService.SLA(GUEST_USER, GUEST_RPS);

    @Override
    public boolean isRequestAllowed(Optional<String> token) {
        log.info("Resolve for token {}", token.orElseGet((() -> "Empty")));
        SlaService.SLA sla = GUEST_SLA;

        if (token.isPresent()) {
            if (isSlaRequested(token)) {
                sla = getCachesSla(token);
            } else {
                //We don't need to wait because call for sca is more than 5 ms.
                demandSla(token);
            }
        }
        log.info("Token {}, user {}, rpc {}", token.orElseGet((() -> "Empty")), sla.getUser(), sla.getRps());
        return currentCalls(sla) <= sla.getRps();
    }

    private void demandSla(Optional<String> token) {
        //Better add lock because we have chance send request more than one time
        log.info("Send request for token {}", token.get());
        users.put(token.get(), slaService.getSlaByToken(token.get()));
    }

    private SlaService.SLA getCachesSla(Optional<String> token) {
        SlaService.SLA result = caches.computeIfAbsent(token.get(), (s) -> {
            CompletableFuture<SlaService.SLA> slaCompletableFuture = users.get(s);
            if (slaCompletableFuture.isDone()) {
                try {
                    SlaService.SLA sla = slaCompletableFuture.get();
                    log.info("Get for user {} rps {}", sla.getUser(), sla.getRps());
                    return sla;
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
            return null;
        });
        return result == null ? GUEST_SLA : result;
    }

    private boolean isSlaRequested(Optional<String> token) {
        return users.containsKey(token.get());
    }

    private int currentCalls(SlaService.SLA user) {
        ReentrantLock lock = locks.computeIfAbsent(user.getUser(), (s) -> new ReentrantLock());
        lock.lock();
        Map<Long, AtomicInteger> calls = requests.computeIfAbsent(user.getUser(), (s) -> new HashMap<>());
        AtomicInteger count = calls.computeIfAbsent(timeService.currentSeconds(), (i) -> new AtomicInteger(0));
        log.info("For user {} registred calls {}", user.getUser(), count.get());
        lock.unlock();
        return count.incrementAndGet();
    }
}