/**
 * Outbound ports (SPI) owned by the Room module. All gateways the application calls to reach the
 * outside world (persistence state gateways, query projection gateways, etc.) are declared here.
 * Driven adapters implement these interfaces. This replaces any {@code domain/spi} location.
 */
package io.github.ryu200o.eduworkshop.room.internal.application.port.out;
