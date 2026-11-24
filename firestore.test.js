const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_athlete_1";
const BOB_UID = "bob_athlete_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validProfilePayload(uid, ts) {
  return {
    userId: uid,
    displayName: "Luthfie Runner",
    weeklyDistanceGoalKm: 45,
    maxHr: 192,
    restingHr: 52,
    ftpWatts: 245,
    totalDistanceMeters: 0,
    totalWorkouts: 0,
    preferredSport: "RUN",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validWorkoutPayload(uid, ts) {
  return {
    userId: uid,
    title: "Morning Tempo Ridge",
    sportType: "RUN",
    distanceMeters: 10240,
    movingTimeSeconds: 2890,
    elapsedTimeSeconds: 2950,
    elevationGainMeters: 145.5,
    avgPaceSecPerKm: 282,
    avgSpeedKmh: 12.75,
    avgHeartRate: 162,
    maxHeartRate: 181,
    avgCadence: 176,
    calories: 690,
    relativeEffort: 118,
    perceivedExertion: 7,
    kudosCount: 0,
    kudosGiven: false,
    privateActivity: false,
    prCount: 2,
    hrZoneSeconds: [120, 480, 960, 1080, 250],
    routePoints: [
      { lat: -6.2088, lng: 106.8456, ele: 12.0, hr: 142 },
      { lat: -6.2102, lng: 106.8481, ele: 18.5, hr: 164 },
    ],
    splits: [
      { km: 1, paceSec: 285, eleDiff: 6.5, avgHr: 151 },
    ],
    deviceName: "HUAWEI Band 11 Pro",
    createdAt: ts,
    updatedAt: ts,
  };
}

test("Unauthenticated user cannot read or write profiles or workouts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, ts))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("workouts").get()
  );
});

test("Owner can create & partially update profile, workouts, devices, gear, routes, segments, challenges", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  // Profile create + update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfilePayload(ALICE_UID, ts))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      weeklyDistanceGoalKm: 60,
      photoUrl: "content://media/picker/101",
      birthDateIso: "1995-06-15",
      gender: "MALE",
      heightCm: 174,
      weightKg: 67.5,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      gender: "INVALID_VALUE",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Workout create + partial updates (kudos, AI insight, DEM elevation) + list
  const workoutRef = aliceDb.collection("users").doc(ALICE_UID).collection("workouts").doc("w_001");
  await assertSucceeds(workoutRef.set({
    ...validWorkoutPayload(ALICE_UID, ts),
    sportType: "CYCLE",
    elevationSource: "Copernicus DEM 90m",
    externalId: "hc_huawei_001",
  }));
  await assertSucceeds(
    workoutRef.update({
      kudosCount: 1,
      kudosGiven: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    workoutRef.update({
      aiInsight: "Strong aerobic tempo consistency with minimal cardiac drift.",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(
    workoutRef.update({
      elevationGainMeters: 164.5,
      elevationSource: "Copernicus DEM 90m",
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("workouts").get()
  );

  // Paired Huawei Band 11 Pro device create + battery sync update
  const devRef = aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("huawei_band_11_pro");
  await assertSucceeds(
    devRef.set({
      userId: ALICE_UID,
      name: "HUAWEI Band 11 Pro",
      macAddress: "AA:BB:CC:DD:EE:FF",
      manufacturer: "Huawei Technologies",
      deviceCategory: "Smartband",
      batteryPercent: 86,
      supportsHr: true,
      supportsRsc: true,
      huaweiNative: true,
      lastSyncedLabel: "Just now",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    devRef.update({
      batteryPercent: 84,
      lastSyncedLabel: "Live BLE",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Gear, Route, Segment, Challenge
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("gear").doc("shoe_1").set({
      userId: ALICE_UID,
      name: "Vaporfly Next% 3",
      brand: "Nike",
      gearType: "SHOES",
      distanceMeters: 124500,
      maxDistanceMeters: 650000,
      primaryGear: true,
      retired: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("routes").doc("route_1").set({
      userId: ALICE_UID,
      name: "Senayan Loop Fast 10K",
      sportType: "RUN",
      surfaceType: "Paved",
      distanceMeters: 10050,
      elevationGainMeters: 42,
      estimatedMinutes: 48,
      waypoints: [{ lat: -6.218, lng: 106.802, ele: 12 }],
      starred: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("segments").doc("seg_1").set({
      userId: ALICE_UID,
      name: "GBK Outer Sprint",
      sportType: "WORKOUT",
      distanceMeters: 1200,
      avgGradePercent: 0.8,
      elevationGainMeters: 10,
      prTimeSeconds: 248,
      komTimeSeconds: 215,
      attemptCount: 4,
      starred: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("challenges").doc("ch_1").set({
      userId: ALICE_UID,
      title: "November 100K Run",
      sportType: "RUN",
      targetValue: 100,
      currentValue: 42.5,
      unit: "km",
      periodLabel: "Nov 2026",
      joined: true,
      completed: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  const healthRef = aliceDb.collection("users").doc(ALICE_UID).collection("health_logs").doc("2026-11-22");
  await assertSucceeds(
    healthRef.set({
      userId: ALICE_UID,
      dateKey: "2026-11-22",
      steps: 9420,
      distanceMetersToday: 7450.5,
      activeCalories: 485,
      totalCalories: 2150,
      restingHr: 52,
      latestHr: 68,
      hrvRmssdMs: 62.4,
      spo2Percent: 98.5,
      sleepTotalMinutes: 445,
      sleepDeepMinutes: 98,
      sleepLightMinutes: 235,
      sleepRemMinutes: 92,
      sleepAwakeMinutes: 20,
      sleepScore: 86,
      vo2MaxEstimate: 51.4,
      recoveryScore: 84,
      sourceDevice: "HUAWEI Band 11 Pro",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    healthRef.update({
      steps: 10250,
      distanceMetersToday: 8100.0,
      spo2Percent: 99.0,
      latestHr: 74,
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Cross-user isolation & negative schema/tamper checks fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("workouts").doc("w_alice").set(
      validWorkoutPayload(ALICE_UID, ts)
    )
  );

  // Bob cannot read, list, update, or delete Alice's workouts or devices
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("workouts").doc("w_alice").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("workouts").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("workouts").doc("w_alice").update({
      title: "Hacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost `id` field (@DocumentId protection)
  const withGhostId = { ...validWorkoutPayload(ALICE_UID, ts), id: "w_bad" };
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("workouts").doc("w_bad").set(withGhostId)
  );

  // Reject future client timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("workouts").doc("w_future").set(
      validWorkoutPayload(ALICE_UID, new Date(Date.now() + 60000))
    )
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("workouts").doc("w_alice").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
