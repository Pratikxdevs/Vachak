To win SIH, your MVP must prove you can bridge the Curriculum-to-Tablet gap
without internet. Here is your Vachak Ecosystem MVP scope:

1. The "Vachak Studio" (Web Portal)

  - Purpose: To turn raw content into the .vachak format.
  - The "Worksheet Designer": A simple web page where a user drags an image
    (e.g., a mango) and defines the "Answer" (e.g., "5") in Hindi and Santali.
  - The Packager: A single "Export to Tablet" button.
      - Backend Logic: Bundles the JSON lesson data + images into a ZIP file.
      - Security: Attaches a simple metadata signature so your App knows the
        file is "Verified Official Content."

2. The "Vachak Runtime" (Android Tablet App)

  - The "File Handler": The app has an Import screen. It reads the .vachak file,
    verifies the signature, and moves the assets into the local SQLite Room
    database.
  - The "Interactive Lesson" View:
      - Dual-Script UI: Displaying the Hindi text and Ol Chiki script
        side-by-side.
      - NIPUN Tracker: Every lesson card displays the "Competency Badge" (e.g.,
        M101: Counting).
  - The "Live Bridge": The PTT button for real-time Hindi-to-Santali speech
    translation (using the local sherpa-onnx runtime).
  - The Worksheet Engine: Instead of PDF files, use the deterministic generator.
    It reads the worksheets table in your SQLite DB and renders the "Matching"
    or "Counting" UI using your native Material 3 components.

3. The MVP Feature Set (What you MUST build)

| Component          | MVP Goal                                                                           |
| :----------------- | :--------------------------------------------------------------------------------- |
| **Ingestion**      | One "Demo Lesson" from the Jharkhand JCERT Class 2 Math textbook.                  |
| **Packaging**      | A website button that exports the "Demo Lesson" into a `.vachak` file.             |
| **Android Import** | Android app "File Picker" that accepts the `.vachak` file and populates the DB.    |
| **Interaction**    | Teacher clicks "Start," sees Hindi/Santali text, speaks Hindi, app speaks Santali. |
| **Practice**       | A "Counting Objects" worksheet template that renders dynamically.                  |

4. Why this MVP kills the competition:

1.  Professionalism: You aren't just an "AI Wrapper." You are a Content
    Ecosystem.
2.  Feasibility: You aren't promising to digitize 5 years of content. You are
    proving the pipeline (JCERT PDF -> Vachak Studio -> Tablet -> Student).
3.  Offline Reality: By using .vachak files, you solve the "No Internet" problem
    for the Jharkhand Department of Education. They can mail these files on SD
    cards or put them on a shared drive, and the app "just works."

The "SIH Demo" Workflow (Script for your final presentation):

1.  Scene 1: "We take the official JCERT Class 2 Math syllabus." (Show the PDF).
2.  Scene 2: "We upload it to the Vachak Studio to package it for tribal
    teachers." (Show the web UI generating the .vachak file).
3.  Scene 3: "On the tablet, we import the pack. No internet needed." (Show the
    Android App importing the file).
4.  Scene 4: "The teacher conducts the lesson in Hindi; the tablet bridges the
    gap in Santali." (Show the real-time translation and the dynamic worksheet).

This is the ultimate professional workflow. It moves from government standards
-> content tooling -> offline delivery.
