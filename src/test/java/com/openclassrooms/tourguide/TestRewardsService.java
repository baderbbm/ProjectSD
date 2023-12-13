package com.openclassrooms.tourguide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
//import com.openclassrooms.tourguide.user.User;

public class TestRewardsService {

	@Test
	public void userGetRewards() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

		InternalTestHelper.setInternalUserNumber(0);
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

		User user = new User(UUID.randomUUID(), "jon", "000", "jon@tourGuide.com");
		Attraction attraction = gpsUtil.getAttractions().get(0);
		user.addToVisitedLocations(new VisitedLocation(user.getUserId(), attraction, new Date()));
		tourGuideService.trackUserLocation(user);
		List<UserReward> userRewards = user.getUserRewards();
		tourGuideService.tracker.stopTracking();
		assertTrue(userRewards.size() == 1);
	}

	@Test
	public void isWithinAttractionProximity() {
		GpsUtil gpsUtil = new GpsUtil();
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		Attraction attraction = gpsUtil.getAttractions().get(0);
		assertTrue(rewardsService.isWithinAttractionProximity(attraction, attraction));
	}

	
	// attribue correctement des récompenses à un utilisateur pour sa proximité avec toutes les attractions
	
	// @Disabled // Needs fixed - can throw ConcurrentModificationException
	// ConcurrentModification
	
	@Test
	public void nearAllAttractions() {
		GpsUtil gpsUtil = new GpsUtil();
		
		// RewardCentral pour la gestion des récompenses
		RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());
		
		// Ignorer les considérations de proximité lors du calcul des récompenses pour un utilisateur
		rewardsService.setProximityBuffer(Integer.MAX_VALUE);
		
		// Configurer l'environnement de test avec un seul utilisateur interne 
		InternalTestHelper.setInternalUserNumber(1);
		
		TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);
		
		// Calcul des récompenses pour le premier utilisateur obtenu
		
		rewardsService.calculateRewards(tourGuideService.getAllUsers().get(0));
		
		// Récupère les récompenses associées au premier utilisateur obtenu 
		// à partir du service tourGuideService et les stocke dans la liste userRewards

		List<UserReward> userRewards = tourGuideService.getUserRewards(tourGuideService.getAllUsers().get(0));
		
		tourGuideService.tracker.stopTracking();
		
		// Chaque attraction du système a une récompense associée pour l'utilisateur spécifié
	    assertEquals(gpsUtil.getAttractions().size(), userRewards.size());
	}
}
