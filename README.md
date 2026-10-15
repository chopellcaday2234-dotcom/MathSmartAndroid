# MathSmart Android

MathSmart is a native Android/Java practice app inspired by the supplied Math Genius reference video, with a girly pink/lavender visual design for the research project:

**MathSmart: Enhancing Mathematical Skills Development of Students in Bestlink College of the Philippines**

## App Screenshots

### Home Screen
![MathSmart home screen](Pasted%20Image.png)

### Arithmetic Quiz Screen
![MathSmart arithmetic quiz screen](Pasted%20Image%202.png)

## Reference-aligned flow
- Main menu: Start, Settings, History
- Timed arithmetic quiz
- Immediate correct/wrong feedback
- Correct answer advances after a short delay
- Wrong answer keeps the same question and timer
- Settings for difficulty, operations, and timer (15/30/45 seconds)
- Empty-answer dialog: "Type your answer!"
- Time Over screen with score, best score, Try again, and Main menu
- Pressed/dimmed button feedback
- Slide transition between main screens
- Local history and best-score storage via SharedPreferences

## Android Studio
1. Open the `MathSmartAndroid` folder in Android Studio.
2. Let Gradle sync using the installed Android Studio/Gradle integration.
3. Run the app on an emulator or Android device.

## Notes
- Native Java Android project; no external runtime service is required.
- The app uses local SharedPreferences for settings, best scores, and history.
- The visual design intentionally changes the reference video's original palette to a pink/lavender girly theme while preserving its main interaction flow.
