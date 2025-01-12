package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.dto.AttractionInfo;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import tripPricer.Provider;
import tripPricer.TripPricer;
import rewardCentral.RewardCentral;


@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;
		
		Locale.setDefault(Locale.US);

		if (testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}

	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	public VisitedLocation getUserLocation(User user) {
		VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
				: trackUserLocation(user);
		return visitedLocation;
	}

	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
	}

	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	/*effectue des calculs basés sur les points de récompense de 
	l'utilisateur, puis appelle un service externe (tripPricer) pour obtenir 
	des offres de voyage en fonction des préférences de l'utilisateur
	Les offres sont ensuite associées à l'utilisateur et renvoyées */
	
	
	
	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}

	public VisitedLocation trackUserLocation(User user) {
		VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
		user.addToVisitedLocations(visitedLocation);
		rewardsService.calculateRewards(user);
		return visitedLocation;
	}
	
	 
	public void trackUsersLocationAsync(List<User> users) {
        // Créez un ExecutorService avec le nombre souhaité de threads
        ExecutorService executorService = Executors.newFixedThreadPool(RewardsService.getThreadPoolSize());

        // Créez un CountDownLatch avec un compte égal au nombre d'utilisateurs
        CountDownLatch countDownLatch = new CountDownLatch(users.size());

        // Soumettez des tâches pour chaque utilisateur
        for (User user : users) {
            executorService.submit(() -> {
                trackUserLocation(user);
                countDownLatch.countDown(); // Diminuez le compte lorsque la tâche est terminée
            });
        }

        // Attendez que toutes les tâches soient terminées
        try {
            countDownLatch.await(); // Cela attend indéfiniment jusqu'à ce que toutes les tâches soient terminées (besoin du test)
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Arrêtez l'ExecutorService
        executorService.shutdown();
    }

	
    /*
	public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
		List<Attraction> nearbyAttractions = new ArrayList<>();
		for (Attraction attraction : gpsUtil.getAttractions()) {
		        // Vérifie si l'attraction est dans la proximité de la localisation visitée par l'utilisateur
			if (rewardsService.isWithinAttractionProximity(attraction, visitedLocation.location)) {
				nearbyAttractions.add(attraction);
			}
		}

		return nearbyAttractions;
	}
	*/

	public List<AttractionInfo> getNearByAttractions(VisitedLocation visitedLocation, User user) {
	    List<Attraction> allAttractions = gpsUtil.getAttractions();
	    // Trier la liste allAttractions en fonction de la distance entre chaque attraction et la dernière localisation de l'utilisateur
	    allAttractions.sort(
	            Comparator.comparingDouble(attraction -> rewardsService.getDistance(attraction, visitedLocation.location)));

	    // Obtenir les 5 attractions les plus proches
	    List<Attraction> nearbyAttractions = allAttractions.stream().limit(5).collect(Collectors.toList());

	    // Créer une liste d'objets AttractionInfo pour contenir les informations demandées
	    List<AttractionInfo> result = new ArrayList<>();

	    // Remplir la liste avec les informations de chaque attraction
	    for (Attraction attraction : nearbyAttractions) {
	        double distance = rewardsService.getDistance(attraction, visitedLocation.location);
	       
	      int rewardPoints = rewardsService.getRewardPoints(attraction, user);

	        AttractionInfo attractionInfo = new AttractionInfo(
	                attraction.attractionName,
	                attraction.latitude,
	                attraction.longitude,
	                visitedLocation.location.latitude,
	                visitedLocation.location.longitude,
	                distance,
	                rewardPoints
	        );

	        result.add(attractionInfo);
	    }

	    return result;
	}

	/*	
public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
	List<Attraction> allAttractions = gpsUtil.getAttractions();

	// Trier la liste allAttractions en fonction de la distance entre chaque attraction et la dernière localisation de l'utilisateur

	allAttractions.sort(
			Comparator.comparingDouble(attraction -> rewardsService.getDistance(attraction, visitedLocation.location)));

	// Obtenir les 5 attractions les plus proches

	List<Attraction> nearbyAttractions = allAttractions.stream().limit(5).collect(Collectors.toList());

	return nearbyAttractions;
}

*/

	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				tracker.stopTracking();
			}
		});
	}

	/**********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}
}
