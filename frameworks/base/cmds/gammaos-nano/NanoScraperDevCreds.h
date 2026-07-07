/*
 * Compiled-in ScreenScraper.fr DEVELOPER credentials for the boxart scraper.
 *
 * The real values are NEVER committed to this repository. They live only in the
 * gitignored file NanoScraperDevCreds.gen.h, which is produced locally by
 * gen_ss_creds.py from your (also gitignored) screenscraper_dev.local.txt. The
 * generator XOR-obfuscates the credentials and splits each one into two chunks so
 * they do not appear as readable strings in the shipped image. See
 * screenscraper_dev.local.txt for the one command to run.
 *
 * Without that local file this header defines nothing but a default softname, so
 * the tree still builds for anyone (scraping then falls back to a user account or
 * the persist.gammaos.scraper.ssdevid / .ssdevpw props). NanoMenuScraper.cpp only
 * touches the obfuscated arrays when NANO_SS_HAVE_DEV_CREDS is defined.
 */
#pragma once

#if defined(__has_include)
#  if __has_include("NanoScraperDevCreds.gen.h")
#    include "NanoScraperDevCreds.gen.h"
#  endif
#endif

#ifndef NANO_SS_HAVE_DEV_CREDS
// No local secret present: ship without built-in dev credentials.
#  define NANO_SS_SOFTNAME "gammaos-nano"
#endif
