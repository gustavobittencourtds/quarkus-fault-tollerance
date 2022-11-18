package org.acme.microprofile.faulttolerance;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.jboss.logging.Logger;

@Path("/coffee")
public class CoffeeResource {

    private static final Logger LOGGER = Logger.getLogger(CoffeeResource.class);

    @Inject
    CoffeeRepositoryService coffeeRepository;

    private AtomicLong counter = new AtomicLong(0);

    private Float failRatio = 0.5f;

    @GET
    @Retry(maxRetries = 4, retryOn = RuntimeException.class)
    public List<Coffee> coffees() {
        final Long invocationNumber = counter.getAndIncrement();

        maybeFail(String.format("CoffeeResource#coffees() invocation #%d failed", invocationNumber));

        LOGGER.infof("CoffeeResource#coffees() invocation #%d returning successfully", invocationNumber);
        return coffeeRepository.getAllCoffees();
    }

    @Path("/{id}")
    @GET
    public Response coffeeDetail(int id) {
        final Long invocationNumber = counter.getAndIncrement();

        maybeFail(String.format("CoffeeResource#coffees() invocation #%d failed", invocationNumber));

        LOGGER.infof("CoffeeResource#coffees() invocation #%d returning successfully", invocationNumber);
        Coffee coffee = coffeeRepository.getCoffeeById(id);

        if (coffee == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(coffee).build();
    }

    @Path("/{id}/availability")
    @GET
    public Response availability(int id) {
        final Long invocationNumber = counter.getAndIncrement();

        Coffee coffee = coffeeRepository.getCoffeeById(id);

        // check that coffee with given id exists, return 404 if not
        if (coffee == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        try {
            Integer availability = coffeeRepository.getAvailability(coffee);
            LOGGER.infof("CoffeeResource#availability() invocation #%d returning successfully", invocationNumber);
            return Response.ok(availability).build();
        } catch (RuntimeException e) {
            String message = e.getClass().getSimpleName() + ": " + e.getMessage();
            LOGGER.errorf("CoffeeResource#availability() invocation #%d failed: %s", invocationNumber, message);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(message)
                    .build();
        }
    }

    @GET
    @Path("/{id}/recommendations")
    @Timeout(250)
    @Fallback(fallbackMethod = "fallbackRecommendations")
    public List<Coffee> recommendations(int id) {
        long started = System.currentTimeMillis();
        final long invocationNumber = counter.getAndIncrement();

        try {
            randomDelay();
            LOGGER.infof("CoffeeResource#recommendations() invocation #%d returning successfully", invocationNumber);
            return coffeeRepository.getRecommendations(id);
        } catch (InterruptedException e) {
            LOGGER.errorf("CoffeeResource#recommendations() invocation #%d timed out after %d ms",
                    invocationNumber, System.currentTimeMillis() - started);
            return null;
        }
    }

    public List<Coffee> fallbackRecommendations(int id) {
        LOGGER.info("Falling back to RecommendationResource#fallbackRecommendations()");
        // safe bet, return something that everybody likes
        return Collections.singletonList(coffeeRepository.getCoffeeById(1));
    }

    private void maybeFail(String failureLogMessage) {
        // introduce some artificial failures
        if (new Random().nextFloat() < failRatio) {
            LOGGER.error(failureLogMessage);
            throw new RuntimeException("Resource failure.");
        }
    }

    private void randomDelay() throws InterruptedException {
        Thread.sleep(new Random().nextInt(500));
    }

    void setFailRatio(Float failRatio) {
        this.failRatio = failRatio;
    }

    void resetCounter() {
        this.counter.set(0);
    }

    Long getCounter() {
        return counter.get();
    }
}
