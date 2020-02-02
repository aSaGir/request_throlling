package throttling.tasks.services;

import java.util.Optional;

public interface ThrottlingService {

    boolean isRequestAllowed(Optional<String> token);

}