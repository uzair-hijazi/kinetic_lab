const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_kinetic_1";
const BOB_UID = "bob_kinetic_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
}

function validProfile(uid, ts) {
  return {
    userId: uid,
    displayName: "KineticAce",
    coins: 250,
    hintsRemaining: 3,
    streakDays: 2,
    lastClaimEpochDay: 20400,
    selectedSkin: "cyan",
    unlockedSkins: ["cyan", "amber"],
    totalScore: 4200,
    totalStars: 12,
    levelsCompleted: 4,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validLevelProgress(uid, levelNumber, ts) {
  return {
    userId: uid,
    levelNumber,
    worldIndex: 0,
    bestScore: 1250,
    stars: 3,
    completed: true,
    bestToolsUsed: 2,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validLeaderboardEntry(uid, score, ts) {
  return {
    userId: uid,
    displayName: "KineticAce",
    avatarBadge: "cyan",
    totalScore: score,
    totalStars: 12,
    levelsCompleted: 4,
    isPublic: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validCustomLevel(uid, published, ts) {
  return {
    authorId: uid,
    authorName: "KineticAce",
    title: "Gravity Slingshot",
    description: "Curve around the twin wells.",
    gravityMode: "ZERO_G",
    emitter: { x: 0.15, y: 0.5, angleDeg: 0.0, speed: 0.65 },
    goal: { x: 0.85, y: 0.5, radius: 0.06 },
    toolBudget: { DEFLECTOR: 2, GRAVITY_WELL: 1 },
    elements: [{ type: "WALL", x1: 0.4, y1: 0.2, x2: 0.4, y2: 0.5 }],
    stars: [{ x: 0.5, y: 0.35 }],
    published,
    verified: true,
    playsCount: 0,
    likesCount: 0,
    createdAt: ts,
    updatedAt: ts,
  };
}

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

test("Unauthenticated caller is rejected across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("level_progress").get());
  await assertFails(unauthDb.collection("leaderboard").where("isPublic", "==", true).get());
  await assertFails(unauthDb.collection("custom_levels").where("published", "==", true).get());
});

test("User profile: owner can create and update, cannot shadow-write or read another user", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTimestamp();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID, ts))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      coins: 500,
      updatedAt: pastTimestamp(),
    })
  );

  // Ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastTimestamp(),
    })
  );

  // Cross-user read & write rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ coins: 999, updatedAt: pastTimestamp() })
  );
});

test("Level progress subcollection: owner CRUD works, cross-user blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("level_progress")
      .doc("level_1")
      .set(validLevelProgress(ALICE_UID, 1, pastTimestamp()))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("level_progress").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("level_progress").get()
  );
});

test("Leaderboard: owner writes own entry, signed-in users query with totalScore filter, cannot spoof others", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb
      .collection("leaderboard")
      .doc(ALICE_UID)
      .set(validLeaderboardEntry(ALICE_UID, 4200, pastTimestamp()))
  );

  await assertSucceeds(
    bobDb
      .collection("leaderboard")
      .where("isPublic", "==", true)
      .limit(20)
      .get()
  );

  await assertFails(bobDb.collection("leaderboard").get());

  await assertFails(
    bobDb
      .collection("leaderboard")
      .doc(ALICE_UID)
      .set(validLeaderboardEntry(ALICE_UID, 99999, pastTimestamp()))
  );
});

test("Custom levels: private drafts isolated, published readable & playable, non-author cannot edit layout", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb
      .collection("custom_levels")
      .doc("draft_1")
      .set(validCustomLevel(ALICE_UID, false, pastTimestamp()))
  );
  await assertFails(bobDb.collection("custom_levels").doc("draft_1").get());
  await assertSucceeds(
    aliceDb.collection("custom_levels").where("authorId", "==", ALICE_UID).get()
  );

  await assertSucceeds(
    aliceDb
      .collection("custom_levels")
      .doc("pub_1")
      .set(validCustomLevel(ALICE_UID, true, pastTimestamp()))
  );
  await assertSucceeds(
    bobDb.collection("custom_levels").where("published", "==", true).get()
  );
  await assertSucceeds(
    bobDb.collection("custom_levels").doc("pub_1").update({
      likesCount: 1,
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(
    bobDb.collection("custom_levels").doc("pub_1").update({
      title: "Hacked",
      updatedAt: pastTimestamp(),
    })
  );
  await assertFails(bobDb.collection("custom_levels").get());
});
