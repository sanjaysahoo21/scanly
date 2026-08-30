/**
 * CORS is configured once in {@link SecurityConfig}. Keeping a second filter
 * here caused inconsistent preflight responses between environments.
 */
package com.scanly.backend.config;
