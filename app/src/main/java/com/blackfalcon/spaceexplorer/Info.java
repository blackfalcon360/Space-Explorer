package com.blackfalcon.spaceexplorer;

import android.graphics.Color;

/** Kid-friendly facts. Everything is stored in the app, so it works offline. */
public final class Info {
    public final String name, tagline, fun;
    public final int color;
    public final String[][] facts;

    private Info(String name, int color, String tagline, String[][] facts, String fun) {
        this.name = name; this.color = color; this.tagline = tagline; this.facts = facts; this.fun = fun;
    }

    public static Info byName(String n) {
        for (Info i : LIST) if (i.name.equals(n)) return i;
        return LIST[0];
    }

    private static final String DEG = "\u00B0";
    private static final String MINUS = "\u2212";

    public static final Info[] LIST = {
        new Info("Sun", Color.parseColor("#FFC107"), "Our giant glowing star", new String[][]{
                {"What is it?", "A star \u2014 a huge ball of hot gas"},
                {"Size", "1,391,000 km wide (109 Earths in a row)"},
                {"Distance from Earth", "150 million km"},
                {"Surface heat", "about 5,500" + DEG + "C"},
                {"Age", "about 4.6 billion years"}},
                "The Sun holds almost all the stuff in our solar system (99.8%)! More than a million Earths could fit inside it. \u26A0\uFE0F Never look straight at the Sun \u2014 it can hurt your eyes."),

        new Info("Mercury", Color.parseColor("#B0B0B0"), "The tiny, speedy planet", new String[][]{
                {"Size", "4,879 km wide (smallest planet)"},
                {"Distance from Sun", "58 million km"},
                {"One day", "59 Earth days"},
                {"One year", "88 Earth days"},
                {"Moons", "0"},
                {"Temperature", MINUS + "180" + DEG + "C to 430" + DEG + "C"}},
                "Mercury zooms around the Sun in just 88 days \u2014 a year there is only about three months long!"),

        new Info("Venus", Color.parseColor("#E8CDA2"), "The super-hot twin", new String[][]{
                {"Size", "12,104 km wide (almost like Earth)"},
                {"Distance from Sun", "108 million km"},
                {"One day", "243 Earth days"},
                {"One year", "225 Earth days"},
                {"Moons", "0"},
                {"Temperature", "about 465" + DEG + "C"}},
                "A day on Venus is longer than its year! It spins backwards, so the Sun rises in the west. It is the brightest planet in our sky \u2014 people call it the Evening Star."),

        new Info("Earth", Color.parseColor("#3FA7FF"), "Our home sweet home", new String[][]{
                {"Size", "12,742 km wide"},
                {"Distance from Sun", "150 million km"},
                {"One day", "24 hours"},
                {"One year", "365 days"},
                {"Moons", "1"},
                {"Temperature", "average about 15" + DEG + "C"}},
                "Earth is the only place we know that has life. About 70% of it is covered with water!"),

        new Info("Mars", Color.parseColor("#E2563B"), "The red planet", new String[][]{
                {"Size", "6,779 km wide"},
                {"Distance from Sun", "228 million km"},
                {"One day", "24 hours 37 minutes"},
                {"One year", "687 Earth days"},
                {"Moons", "2 (Phobos and Deimos)"},
                {"Temperature", "average about " + MINUS + "60" + DEG + "C"}},
                "Mars looks red because its dust is full of rusty iron. It has Olympus Mons, a volcano more than twice as tall as Mount Everest!"),

        new Info("Jupiter", Color.parseColor("#D9A066"), "The giant of the solar system", new String[][]{
                {"Size", "139,820 km wide (11 Earths in a row)"},
                {"Distance from Sun", "778 million km"},
                {"One day", "about 10 hours"},
                {"One year", "12 Earth years"},
                {"Moons", "more than 90"},
                {"Temperature", "about " + MINUS + "110" + DEG + "C (cloud tops)"}},
                "Jupiter is so big that all the other planets could fit inside it! Its Great Red Spot is a storm bigger than Earth that has been blowing for hundreds of years."),

        new Info("Saturn", Color.parseColor("#E3C77D"), "The planet with beautiful rings", new String[][]{
                {"Size", "116,460 km wide (9 Earths in a row)"},
                {"Distance from Sun", "1.4 billion km"},
                {"One day", "about 10.7 hours"},
                {"One year", "29 Earth years"},
                {"Moons", "more than 140"},
                {"Temperature", "about " + MINUS + "140" + DEG + "C"}},
                "Saturn's rings are made of billions of pieces of ice and rock. Saturn is so light for its size that it would float in a giant bathtub of water!"),

        new Info("Uranus", Color.parseColor("#7DE3E3"), "The sideways planet", new String[][]{
                {"Size", "50,724 km wide (4 Earths in a row)"},
                {"Distance from Sun", "2.9 billion km"},
                {"One day", "about 17 hours"},
                {"One year", "84 Earth years"},
                {"Moons", "more than 25"},
                {"Temperature", "about " + MINUS + "195" + DEG + "C"}},
                "Uranus spins on its side, like a ball rolling around the Sun! It looks blue-green because of a gas called methane."),

        new Info("Neptune", Color.parseColor("#4B70DD"), "The windy blue giant", new String[][]{
                {"Size", "49,244 km wide (4 Earths in a row)"},
                {"Distance from Sun", "4.5 billion km"},
                {"One day", "about 16 hours"},
                {"One year", "165 Earth years"},
                {"Moons", "16 known"},
                {"Temperature", "about " + MINUS + "200" + DEG + "C"}},
                "Neptune has the fastest winds in the solar system \u2014 over 2,000 km per hour! People found it using maths before anyone saw it through a telescope."),

        new Info("Moon", Color.parseColor("#E0E0E0"), "Earth's best friend", new String[][]{
                {"Size", "3,474 km wide (about 1/4 of Earth)"},
                {"Distance from Earth", "384,400 km"},
                {"Goes around Earth", "every 27 days"},
                {"Phases repeat", "every 29.5 days"},
                {"Air", "none"},
                {"Temperature", MINUS + "170" + DEG + "C to 120" + DEG + "C"}},
                "The Moon makes no light of its own \u2014 it shines by reflecting sunlight! Astronauts have walked on it, and their footprints stay for a very long time because there is no wind.")
    };
}
