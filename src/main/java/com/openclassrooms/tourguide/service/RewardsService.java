package com.openclassrooms.tourguide.service;

import java.util.ArrayList;
import java.util.List;


import org.springframework.stereotype.Service;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class RewardsService {
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

	// proximity in miles
    private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	
	private static final int THREAD_POOL_SIZE = 40;

	
	public static int getThreadPoolSize() {
		return THREAD_POOL_SIZE;
	}

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}
	
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}
	
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}
	
	
	// parcourir la liste des emplacements visités par l'utilisateur (userLocations) 
	// et des attractions, tout en modifiant la liste des récompenses de l'utilisateur (user.getUserRewards())
	// vous modifiez la liste user.getUserRewards() en ajoutant des éléments à l'intérieur de la boucle	\

	/*
	public void calculateRewards(User user) {
		// List<VisitedLocation> userLocations = user.getVisitedLocations();
		List<VisitedLocation> userLocations = new ArrayList<>(user.getVisitedLocations());
		List<Attraction> attractions = gpsUtil.getAttractions();
		
		List<UserReward> newRewards = new ArrayList<UserReward>();
		for (VisitedLocation visitedLocation : userLocations) {
			
			for (Attraction attraction : attractions) {
			//if (user.getUserRewards().stream().filter(r -> r.attraction.attractionName.equals(attraction.attractionName)).count() == 0) {
				if (newRewards.stream().filter(r -> r.attraction.attractionName.equals(attraction.attractionName)).count() == 0) {
					if (nearAttraction(visitedLocation, attraction)) {
						// user.addUserReward(new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)));
						newRewards.add(new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)));
					}
				}
			}
		}	
		user.setUserRewards(newRewards);
	}
	
	
	*/
	public void calculateRewards(User user) {
	    List<VisitedLocation> userLocations = new ArrayList<>(user.getVisitedLocations());
	    List<Attraction> attractions = gpsUtil.getAttractions();

	    List<UserReward> newRewards = new ArrayList<>();

	    userLocations.stream()
	        // Applique une fonction à chaque élément du flux (chaque VisitedLocation), produisant un flux interne
	        // pour chaque VisitedLocation. Le flux interne est créé à partir des attractions
	        // qui sont proches de la VisitedLocation actuelle
	        .flatMap(visitedLocation ->
	            attractions.stream()
	                .filter(attraction -> nearAttraction(visitedLocation, attraction))
	                .filter(attraction -> newRewards.stream()
	                		// si aucun élément du flux newRewards correspond à l'attraction actuelle
	                        .noneMatch(r -> r.attraction.attractionName.equals(attraction.attractionName)))
	                // Transforme chaque attraction filtrée en un objet UserReward
	                .map(attraction -> new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user)))
	        )
	        // Ajoute chaque UserReward résultant à la liste newRewards
	        .forEach(newRewards::add);

	    user.setUserRewards(newRewards);
	}


	
	// Un pool de 50 threads est créé.
	// Ensuite, une tâche est soumise à ce pool pour chaque utilisateur dans la liste users. 
	// Ces tâches sont exécutées en parallèle, avec un maximum de 50 tâches simultanées 
	// à un moment donné, grâce au pool de threads
	
	public void calculateRewardsAsync(List<User> users) {
        // Créez un ExecutorService avec le nombre souhaité de threads
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        // Créez un CountDownLatch avec un compte égal au nombre d'utilisateurs
        CountDownLatch countDownLatch = new CountDownLatch(users.size());

        // Soumettez des tâches pour chaque utilisateur
        for (User user : users) {
            executorService.submit(() -> {
            	// System.out.println(user.getEmailAddress()+" performed by "+ Thread.currentThread().getName()); 
                calculateRewards(user);
                countDownLatch.countDown(); // Diminuez le compte lorsque la tâche est terminée
            });
        }

        // Attendez que toutes les tâches soient terminées (besoin du test)
        try {
            countDownLatch.await(); // Cela attend indéfiniment jusqu'à ce que toutes les tâches soient terminées
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Arrêtez l'ExecutorService
        executorService.shutdown();
    }

	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}
	
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}
	
	public int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}
	
	public double getDistance(Location loc1, Location loc2) {
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                               + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
        return statuteMiles;
	}

}
