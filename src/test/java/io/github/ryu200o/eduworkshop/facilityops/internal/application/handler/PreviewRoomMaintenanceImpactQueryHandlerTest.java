package io.github.ryu200o.eduworkshop.facilityops.internal.application.handler;

import io.github.ryu200o.eduworkshop.facilityops.internal.application.exception.FacilityRoomNotFoundException;
import io.github.ryu200o.eduworkshop.facilityops.internal.application.port.inbound.query.PreviewRoomMaintenanceImpactQuery;
import io.github.ryu200o.eduworkshop.facilityops.internal.application.port.inbound.query.view.RoomMaintenanceImpactView;
import io.github.ryu200o.eduworkshop.registration.RegistrationExposeAPI;
import io.github.ryu200o.eduworkshop.room.RoomExposeAPI;
import io.github.ryu200o.eduworkshop.workshop.WorkshopExposeAPI;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopStateContract;
import io.github.ryu200o.eduworkshop.workshop.contract.WorkshopImpactContract;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreviewRoomMaintenanceImpactQueryHandlerTest {

    @Mock
    private RoomExposeAPI roomExposeAPI;

    @Mock
    private WorkshopExposeAPI workshopExposeAPI;

    @Mock
    private RegistrationExposeAPI registrationExposeAPI;

    private PreviewRoomMaintenanceImpactQueryHandler handler() {
        return new PreviewRoomMaintenanceImpactQueryHandler(roomExposeAPI, workshopExposeAPI, registrationExposeAPI);
    }

    private static PreviewRoomMaintenanceImpactQuery query(UUID roomId) {
        return new PreviewRoomMaintenanceImpactQuery(
                roomId,
                Instant.parse("2026-08-01T08:00:00Z"),
                Instant.parse("2026-08-01T12:00:00Z"));
    }

    private static WorkshopImpactContract workshop(UUID id, WorkshopStateContract state) {
        return new WorkshopImpactContract(id, state, false, null);
    }

    @Test
    void roomNotFound_throwsFacilityRoomNotFoundException() {
        UUID roomId = UUID.randomUUID();
        when(roomExposeAPI.existsById(roomId)).thenReturn(false);

        assertThatThrownBy(() -> handler().handle(query(roomId)))
                .isInstanceOf(FacilityRoomNotFoundException.class);
        verify(workshopExposeAPI, never()).findByRoomAndTimeOverlap(any(), any(), any());
    }

    @Test
    void noWorkshops_returnsZeroCounts() {
        UUID roomId = UUID.randomUUID();
        when(roomExposeAPI.existsById(roomId)).thenReturn(true);
        when(workshopExposeAPI.findByRoomAndTimeOverlap(eq(roomId), any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        RoomMaintenanceImpactView view = handler().handle(query(roomId));

        assertThat(view.roomId()).isEqualTo(roomId);
        assertThat(view.publishedWorkshopsCount()).isZero();
        assertThat(view.plannedWorkshopsCount()).isZero();
        assertThat(view.totalAffectedStudentsCount()).isZero();
        verify(registrationExposeAPI, never()).countActiveByWorkshopIds(anyList());
    }

    @Test
    void publishedWorkshopsOverlap_returnsCorrectCount() {
        UUID roomId = UUID.randomUUID();
        UUID ws1 = UUID.randomUUID();
        UUID ws2 = UUID.randomUUID();
        when(roomExposeAPI.existsById(roomId)).thenReturn(true);
        when(workshopExposeAPI.findByRoomAndTimeOverlap(eq(roomId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(
                        workshop(ws1, WorkshopStateContract.PUBLISHED),
                        workshop(ws2, WorkshopStateContract.PUBLISHED)));
        when(registrationExposeAPI.countActiveByWorkshopIds(anyList())).thenReturn(25);

        RoomMaintenanceImpactView view = handler().handle(query(roomId));

        assertThat(view.publishedWorkshopsCount()).isEqualTo(2);
        assertThat(view.plannedWorkshopsCount()).isZero();
        assertThat(view.totalAffectedStudentsCount()).isEqualTo(25);
        verify(registrationExposeAPI).countActiveByWorkshopIds(List.of(ws1, ws2));
    }

    @Test
    void plannedWorkshopsOverlap_returnsCorrectCount() {
        UUID roomId = UUID.randomUUID();
        when(roomExposeAPI.existsById(roomId)).thenReturn(true);
        when(workshopExposeAPI.findByRoomAndTimeOverlap(eq(roomId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(
                        workshop(UUID.randomUUID(), WorkshopStateContract.PLANNED),
                        workshop(UUID.randomUUID(), WorkshopStateContract.PLANNED)));

        RoomMaintenanceImpactView view = handler().handle(query(roomId));

        assertThat(view.publishedWorkshopsCount()).isZero();
        assertThat(view.plannedWorkshopsCount()).isEqualTo(2);
        assertThat(view.totalAffectedStudentsCount()).isZero();
        verify(registrationExposeAPI, never()).countActiveByWorkshopIds(anyList());
    }

    @Test
    void mixedWorkshops_returnsCorrectCounts() {
        UUID roomId = UUID.randomUUID();
        when(roomExposeAPI.existsById(roomId)).thenReturn(true);
        when(workshopExposeAPI.findByRoomAndTimeOverlap(eq(roomId), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(
                        workshop(UUID.randomUUID(), WorkshopStateContract.PUBLISHED),
                        workshop(UUID.randomUUID(), WorkshopStateContract.PLANNED),
                        workshop(UUID.randomUUID(), WorkshopStateContract.PUBLISHED)));
        when(registrationExposeAPI.countActiveByWorkshopIds(anyList())).thenReturn(15);

        RoomMaintenanceImpactView view = handler().handle(query(roomId));

        assertThat(view.publishedWorkshopsCount()).isEqualTo(2);
        assertThat(view.plannedWorkshopsCount()).isEqualTo(1);
        assertThat(view.totalAffectedStudentsCount()).isEqualTo(15);
    }

    @Test
    void noOverlap_returnsZeroCounts() {
        UUID roomId = UUID.randomUUID();
        when(roomExposeAPI.existsById(roomId)).thenReturn(true);
        when(workshopExposeAPI.findByRoomAndTimeOverlap(eq(roomId), any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        RoomMaintenanceImpactView view = handler().handle(query(roomId));

        assertThat(view.publishedWorkshopsCount()).isZero();
        assertThat(view.plannedWorkshopsCount()).isZero();
        assertThat(view.totalAffectedStudentsCount()).isZero();
    }
}
