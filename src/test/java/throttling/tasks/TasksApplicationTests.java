package throttling.tasks;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.util.Assert;
import throttling.tasks.services.SlaService;
import throttling.tasks.services.ThrottlingService;
import throttling.tasks.services.impl.ThrottlingServiceImpl;
import throttling.tasks.services.impl.TimeService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class TasksApplicationTests {

    private ThrottlingService sut;
    private SlaService slaService;


    @BeforeEach
    public void setUp() {
        slaService = Mockito.mock(SlaService.class);
        TimeService timeService = Mockito.mock(TimeService.class);
        sut = new ThrottlingServiceImpl(slaService, timeService);
        Mockito.when(timeService.currentSeconds()).thenReturn(1000L);
    }

    @Test
    public void simpleTest() {

        Assert.isTrue(sut.isRequestAllowed(Optional.empty()));

    }

    @Test
    public void guestMoreThen10Times() throws InterruptedException, ExecutionException {
        Mockito.when(slaService.getSlaByToken(any())).thenReturn(compl());

        CountDownLatch latch = new CountDownLatch(10);
        List<CompletableFuture> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(runCall(latch, i));
        }

        CompletableFuture<Void> complete = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[futures.size()]));
        complete.get();

        Assertions.assertFalse(sut.isRequestAllowed(Optional.of("test")));

    }

    @Test
    public void userMoreThen10Times() throws InterruptedException, ExecutionException {
        CountDownLatch latch1 = new CountDownLatch(1);
        Mockito.when(slaService.getSlaByToken(eq("token_1"))).thenReturn(compl("user_1", latch1));
        sut.isRequestAllowed(Optional.of("token_1"));
        latch1.await();

        CountDownLatch latch = new CountDownLatch(6);
        List<CompletableFuture> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            futures.add(runCall(latch, i));
        }

        CompletableFuture<Void> complete = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[futures.size()]));
        complete.get();

        Assertions.assertFalse(sut.isRequestAllowed(Optional.of("token_1")));
    }

    private CompletableFuture runCall(CountDownLatch latch, int i) {
        return CompletableFuture.runAsync(() -> {
            latch.countDown();
			System.out.println("2 latch " + latch.getCount() + " i = " + i);
            Assertions.assertTrue(sut.isRequestAllowed(Optional.of("token_1")));
        });
    }

    private CompletableFuture<SlaService.SLA> compl(String user, CountDownLatch latch) {
        return CompletableFuture.supplyAsync(() -> {
            latch.countDown();
            return new SlaService.SLA(user, 6);
        });
    }

    private CompletableFuture<SlaService.SLA> compl() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return new SlaService.SLA("user_1", 30);
        });
    }

}
