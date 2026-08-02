package com.kosa.fillinv.schedule.service;

import com.kosa.fillinv.global.exception.ResourceException;
import com.kosa.fillinv.member.dto.profile.ProfileResponseDto;
import com.kosa.fillinv.member.service.MemberService;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ScheduleParticipantProfileReader {

    private final MemberService memberService;

    Map<String, ProfileResponseDto> getProfiles(Collection<String> memberIds) {
        return memberService.getAllProfilesByMemberIds(memberIds);
    }

    String getNickname(String memberId) {
        ProfileResponseDto profile = getProfiles(Set.of(memberId))
                .get(memberId);
        if (profile == null) {
            throw new ResourceException.NotFound("Member profile not found. memberId: " + memberId);
        }

        return profile.nickname();
    }
}
