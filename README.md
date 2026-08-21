# JalRakshak

JalRakshak is a flood early warning and response system built for the Smart India Hackathon 2026 under the Disaster Management theme. It helps district officials and citizens get flood risk information at the village level, instead of one common alert for the whole district. Officials get a live web dashboard and citizens get an Android app that warns them early and tells them what to do.

## Why we built this

Right now when a flood is coming, most people get a single SMS or a siren that says danger. It does not tell you whether your own village is at risk, how much time you have, or where to go. Officials also have information spread across many places instead of one screen. We wanted to fix that gap.

JalRakshak looks at rainfall and river levels for each village, compares them against the government warning and danger marks, and decides a risk level for that village. When an area becomes risky, the officer can send a targeted alert that reaches only the people in that village, along with clear advice in their own language. Citizens can also send an SOS back, so it works both ways.

This is an early warning system, not a prediction system. It does not claim to know weeks in advance. It watches live conditions and warns the moment things cross the danger threshold, which is the window that actually saves lives.

## What it does

For officials, through the web dashboard:
* A live map of Assam with every village shown as a green, yellow or red dot based on its current risk
* Summary numbers for how many villages are in danger, how many people are at risk, and pending relief requests
* Charts for rainfall trends and affected population over time
* A relief request list and a live activity feed
* A replay mode that plays back the 2022 Assam flood day by day, so you can see how the system reacts as water rises
* One click to send an alert to a chosen village

For citizens, through the Android app:
* Pick your village once and see its current risk on the home screen
* Clear advice on what to do, in Hindi or English
* An SOS button to ask for help, which reaches the officer dashboard
* Push notifications so an alert reaches the phone even when the app is closed
* Offline support, so if the network goes down during a flood the app still shows the last known risk and advice

## How the risk is decided

All the risk logic lives in one place in the backend so it stays consistent and easy to check. For each village the system looks at three things. First, the rainfall for that area. Second, the river level compared against that station's warning and danger marks. Third, the elevation, since low lying villages flood faster. Based on these it marks the village green, yellow or red and gives a short reason and a rough estimate of how soon water may rise further.

## Data sources

Everything runs on free and public data.
* Live rainfall comes from the Open Meteo weather service, which needs no key
* The flood replay uses representative data for the 2022 Assam flood period so the demo can show a full flood rising and receding
* River warning and danger levels are based on Central Water Commission thresholds
* Village locations and elevation come from open sources

Where a value is a demo stand in rather than an official live reading, the interface says so clearly. River levels are shown as estimates, and villages without a known river threshold are marked accordingly rather than filled with made up numbers.

## Tech stack

The backend is built with Laravel and MySQL and works as a JSON API. The officer dashboard is a React app using Leaflet for the map and Chart.js for the charts. The citizen app is native Android written in Kotlin with Jetpack Compose, using Room for offline storage. Push notifications use Firebase Cloud Messaging.

## Project structure

```
backend/ Laravel API, risk engine, data seeders
dashboard/ React officer dashboard
app/ Kotlin Android citizen app
data/ Assam villages, river stations, replay dataset, shelters
```


## Running it locally

You need PHP 8.3, Composer, Node 18 or higher, MySQL, and Android Studio for the app.

Backend:

```
cd backend
cp .env.example .env
php artisan key:generate
php artisan migrate --seed
php artisan serve
```


Dashboard:

```
cd dashboard
npm install
npm run dev
```


App:
Open the app folder in Android Studio, use an emulator with Google Play services, and run.

## Team

Built by Team Blackbox for Smart India Hackathon 2026.
