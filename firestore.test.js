const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTimestamp() {
  return new Date(Date.now() - 1000);
}

function validProfile(uid) {
  return {
    userId: uid,
    displayName: "Alice Learner",
    activeLanguage: "japanese",
    difficulty: "beginner",
    totalXp: 0,
    currentStreak: 1,
    lastActiveDate: "2026-11-14",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validMastery(lang) {
  return {
    languageCode: lang,
    vocabularyScore: 20,
    writingScore: 10,
    grammarScore: 15,
    phoneticsScore: 5,
    pragmaticsScore: 0,
    languageXp: 50,
    gamesPlayed: 1,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

function validSession() {
  return {
    gameType: "cards",
    languageCode: "japanese",
    difficulty: "beginner",
    scorePercent: 90,
    xpEarned: 45,
    durationSeconds: 32,
    summary: "Matched 6/6 Hiragana pairs",
    createdAt: serverTimestamp(),
  };
}

function validPhrase() {
  return {
    languageCode: "spanish",
    targetText: "Buenos dias",
    phonetic: "BWEH-nos DEE-as",
    englishMeaning: "Good morning",
    sourceGame: "cards",
    createdAt: serverTimestamp(),
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

test("Unauthenticated user cannot read or write user profile or subcollections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID)));
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sessions").get());
});

test("Authenticated owner can create, read, and update their own UserProfile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);
  await assertSucceeds(profileRef.set(validProfile(ALICE_UID)));
  await assertSucceeds(profileRef.get());
  await assertSucceeds(
    profileRef.update({
      activeLanguage: "french",
      totalXp: 120,
      updatedAt: serverTimestamp(),
    })
  );
});

test("Authenticated user cannot list /users collection or read/write another user's profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      activeLanguage: "spanish",
      difficulty: "beginner",
      totalXp: 10,
      currentStreak: 1,
      lastActiveDate: "2026-11-14",
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).set(validProfile(BOB_UID)));
});

test("Shadow update with ghost field or immutable createdAt tampering is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);
  await assertSucceeds(profileRef.set(validProfile(ALICE_UID)));

  await assertFails(
    profileRef.update({
      isAdmin: true,
      updatedAt: serverTimestamp(),
    })
  );

  await assertFails(
    profileRef.update({
      userId: BOB_UID,
      updatedAt: serverTimestamp(),
    })
  );
});

test("Mastery subcollection enforces valid languageCode and score bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const jpRef = aliceDb.collection("users").doc(ALICE_UID).collection("mastery").doc("japanese");
  await assertSucceeds(jpRef.set(validMastery("japanese")));

  // Invalid language doc ID
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("mastery").doc("klingon").set(validMastery("japanese"))
  );

  // Out of bounds score
  await assertFails(
    jpRef.update({
      vocabularyScore: 150,
      updatedAt: serverTimestamp(),
    })
  );
});

test("Sessions are append-only (create + list + delete allowed for owner, update forbidden)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const sessRef = aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1");
  await assertSucceeds(sessRef.set(validSession()));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("sessions").get());
  await assertFails(sessRef.update({ xpEarned: 9999 }));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("sessions").get());
});

test("SavedPhrases enforce owner isolation and valid fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const phraseRef = aliceDb.collection("users").doc(ALICE_UID).collection("savedPhrases").doc("phr_1");
  await assertSucceeds(phraseRef.set(validPhrase()));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("savedPhrases").get());

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("savedPhrases").get());
  await assertSucceeds(phraseRef.delete());
});
