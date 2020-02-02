package throttling.tasks.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Calendar;

@Service
@Slf4j
public class TimeService {

    public long currentSeconds() {
        long second = Calendar.getInstance().getTimeInMillis() / 1000L;
        log.info("Second {}", second);
        return second;
    }
}