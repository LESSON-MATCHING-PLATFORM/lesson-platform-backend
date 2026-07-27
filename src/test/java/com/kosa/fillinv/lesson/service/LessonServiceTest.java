package com.kosa.fillinv.lesson.service;

import com.kosa.fillinv.category.entity.Category;
import com.kosa.fillinv.category.repository.CategoryRepository;
import com.kosa.fillinv.global.exception.ResourceException;
import com.kosa.fillinv.lesson.entity.AvailableTime;
import com.kosa.fillinv.lesson.entity.Lesson;
import com.kosa.fillinv.lesson.entity.Option;
import com.kosa.fillinv.lesson.repository.AvailableTimeRepository;
import com.kosa.fillinv.lesson.repository.LessonRepository;
import com.kosa.fillinv.lesson.repository.OptionRepository;
import com.kosa.fillinv.lesson.service.dto.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.kosa.fillinv.lesson.entity.LessonType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

@Transactional
@SpringBootTest
@ActiveProfiles("local")
class LessonServiceTest {

    @Autowired
    private LessonService lessonService;

    @Autowired
    private LessonRepository lessonRepository;
    @Autowired
    private OptionRepository optionRepository;
    @Autowired
    private AvailableTimeRepository availableTimeRepository;
    @Autowired
    private CategoryRepository categoryRepository;


    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("레슨을 생성한다.")
    void createLesson() {
        // given
        CreateLessonCommand command = createCommand();

        // when
        CreateLessonResult lesson = lessonService.createLesson(command);
        entityManager.flush();
        entityManager.clear();

        // then
        Lesson result = lessonRepository.findById(lesson.id()).orElseThrow();
        assertEquals(command.title(), result.getTitle());
        assertEquals(command.description(), result.getDescription());
        assertEquals(command.lessonType(), result.getLessonType());
        assertEquals(command.thumbnailImage(), result.getThumbnailImage());
        assertEquals(command.location(), result.getLocation());
        assertEquals(command.mentorId(), result.getMentorId());
        assertEquals(command.categoryId(), result.getCategoryId());
        assertEquals(command.closeAt(), result.getCloseAt());
        assertEquals(command.availableTimeCommandList().size(), result.getAvailableTimeList().size());
        assertEquals(command.optionCommandList().size(), result.getOptionList().size());
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource(
            value = {
                    // testName | title | lessonType | thumbnail | description | location | mentorId | categoryId
                    "title 누락,        , MENTORING, ONE.png, 설명, 서울, mentor-1, 1",
                    "lessonType 누락,  제목,        , ONE.png, 설명, 서울, mentor-1, 1",
                    "thumbnail 누락,   제목, MENTORING,        , 설명, 서울, mentor-1, 1",
                    "description 누락, 제목, MENTORING, ONE.png,        , 서울, mentor-1, 1",
                    "mentorId 누락,    제목, MENTORING, ONE.png, 설명, 서울,        , 1",
                    "categoryId 누락,  제목, MENTORING, ONE.png, 설명, 서울, mentor-1, "
            },
            nullValues = {""}
    )
    @DisplayName("레슨 필수 항목이 누락되면 예외를 던진다.")
    void createLessonWithMissingRequiredField(
            String caseName,
            String title,
            LessonType lessonType,
            String thumbnailImage,
            String description,
            String location,
            String mentorId,
            Long categoryId
    ) {
        // given
        CreateLessonCommand command = createCommand(
                title,
                lessonType,
                thumbnailImage,
                description,
                location,
                mentorId,
                categoryId,
                Collections.EMPTY_LIST,
                Collections.EMPTY_LIST
        );

        // when & then
        assertThatThrownBy(() -> lessonService.createLesson(command))
                .isInstanceOf(ResourceException.InvalidArgument.class);
    }

    @Test
    @DisplayName("id로 레슨을 조회한다.")
    void readLessonById() {
        // given
        CreateLessonResult created = lessonService.createLesson(createCommand());
        entityManager.flush();
        entityManager.clear();

        // when
        LessonDTO result = lessonService.readLessonById(created.id()).orElseThrow();

        // then
        assertEquals(created.id(), result.id());
        assertEquals(created.title(), result.title());
        assertEquals(created.description(), result.description());
        assertEquals(created.lessonType(), result.lessonType());
        assertEquals(created.thumbnailImage(), result.thumbnailImage());
        assertEquals(created.location(), result.location());
        assertEquals(created.mentorId(), result.mentorId());
        assertEquals(created.categoryId(), result.categoryId());
        assertEquals(created.closeAt(), result.closeAt());
        assertEquals(created.availableTimeDTOList().size(), result.availableTimeDTOList().size());
        assertEquals(created.optionResultList().size(), result.optionDTOList().size());
    }

    @Test
    @DisplayName("레슨 목록을 조회한다.")
    void readLessonAll() {
        // given
        List<LessonDTO> initial = lessonService.readLessonAll();
        lessonService.createLesson(createCommand());
        lessonService.createLesson(createCommand());

        // when
        List<LessonDTO> lessons = lessonService.readLessonAll();

        // then
        assertTrue(lessons.size() >= initial.size() + 2);
    }

    @Test
    @DisplayName("레슨을 수정한다.")
    void updateLesson() {
        // given
        CreateLessonResult created = lessonService.createLesson(createCommand());

        UpdateLessonCommand updateCommand = new UpdateLessonCommand(
                "title",
                "updated-thumbnail.png",
                "수정된 설명",
                "부산",
                1L,
                null,
                Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(7, ChronoUnit.DAYS)
        );

        // when
        UpdateLessonResult updated =
                lessonService.updateLesson(created.id(), updateCommand, created.mentorId());

        // then
        assertEquals(updateCommand.title(), updated.title());
        assertEquals(updateCommand.thumbnailImage(), updated.thumbnailImage());
        assertEquals(updateCommand.description(), updated.description());
        assertEquals(updateCommand.location(), updated.location());
        assertEquals(updateCommand.categoryId(), updated.categoryId());
        assertEquals(updateCommand.closeAt(), updated.closeAt());
        assertNotNull(updated.updatedAt());
        System.out.println(updated.updatedAt());
    }

    @Test
    @DisplayName("레슨을 삭제한다. 레슨을 삭제하면 포한됨 옵션(Option)과 가능한 시간(availableTime)도 삭제된다")
    void deleteLesson() {
        // given
        CreateLessonResult created = lessonService.createLesson(createCommand());

        // when
        lessonService.deleteLesson(created.id(), created.mentorId());
        entityManager.flush();
        entityManager.clear();

        // then
        Optional<LessonDTO> result = lessonService.readLessonById(created.id());
        List<Option> allByLessonId = optionRepository.findAllByLessonId(created.id()).stream().filter(op -> op.getDeletedAt() == null).toList();
        List<AvailableTime> allByLessonId1 = availableTimeRepository.findAllByLessonId(created.id()).stream().filter(at -> at.getDeletedAt() == null).toList();

        assertTrue(result.isEmpty());
        assertTrue(allByLessonId.isEmpty());
        assertTrue(allByLessonId1.isEmpty());
    }

    @Test
    @DisplayName("레슨에 가능한 시간(availableTime)을 추가한다.")
    void addAvailableTime() {
        // given
        CreateLessonResult created = lessonService.createLesson(createCommand());
        CreateAvailableTimeCommand command = createAvailableTimeCommand();

        // when
        lessonService.addAvailableTime(created.id(), command);
        entityManager.flush();
        entityManager.clear();

        LessonDTO result = lessonService.readLessonById(created.id()).orElseThrow();

        // then
        assertEquals(created.availableTimeDTOList().size() + 1, result.availableTimeDTOList().size());
        assertTrue(
                result.availableTimeDTOList().stream()
                        .anyMatch(at ->
                                at.startTime().equals(command.startTime()) &&
                                        at.endTime().equals(command.endTime()) &&
                                        at.price().equals(command.price()) &&
                                        at.lessonId().equals(created.id())
                        )
        );
    }

    @Test
    @DisplayName("레슨에 가능한 시간(availableTime)을 여러개 추가한다.")
    void addAvailableTimeAll() {
        // given
        CreateLessonResult created = lessonService.createLesson(createCommand());

        Instant start1 = toInstant(LocalDateTime.of(2025, 1, 10, 14, 0));
        Instant end1 = toInstant(LocalDateTime.of(2025, 1, 10, 16, 0));

        Instant start2 = toInstant(LocalDateTime.of(2025, 1, 17, 14, 0));
        Instant end2 = toInstant(LocalDateTime.of(2025, 1, 17, 16, 0));

        List<CreateAvailableTimeCommand> commands = List.of(
                createAvailableTimeCommand(start1, end1, 1000),
                createAvailableTimeCommand(start2, end2, 2000)
        );

        // when
        lessonService.addAvailableTime(created.id(), commands);
        entityManager.flush();
        entityManager.clear();

        LessonDTO result = lessonService.readLessonById(created.id()).orElseThrow();

        // then
        assertEquals(created.availableTimeDTOList().size() + commands.size(), result.availableTimeDTOList().size());

        assertTrue(
                result.availableTimeDTOList().stream()
                        .anyMatch(at ->
                                at.startTime().equals(start1.truncatedTo(ChronoUnit.MINUTES)) &&
                                        at.endTime().equals(end1.truncatedTo(ChronoUnit.MINUTES)) &&
                                        at.price() == 1000
                        )
        );

        assertTrue(
                result.availableTimeDTOList().stream()
                        .anyMatch(at ->
                                at.startTime().equals(start2.truncatedTo(ChronoUnit.MINUTES)) &&
                                        at.endTime().equals(end2.truncatedTo(ChronoUnit.MINUTES)) &&
                                        at.price() == 2000
                        )
        );

    }

    @Test
    @DisplayName("레슨에서 가능한 시간(availableTime)을 삭제한다")
    void deleteAvailableTime() {
        // given
        CreateLessonResult created = lessonService.createLesson(createCommand());

        Instant start1 = toInstant(LocalDateTime.of(2025, 1, 10, 14, 0));
        Instant end1 = toInstant(LocalDateTime.of(2025, 1, 10, 16, 0));

        Instant start2 = toInstant(LocalDateTime.of(2025, 1, 17, 14, 0));
        Instant end2 = toInstant(LocalDateTime.of(2025, 1, 17, 16, 0));

        List<CreateAvailableTimeCommand> commands = List.of(
                createAvailableTimeCommand(start1, end1, 1000),
                createAvailableTimeCommand(start2, end2, 2000)
        );

        List<CreateAvailableTimeResult> createAvailableTimeResults = lessonService.addAvailableTime(created.id(), commands);

        // when
        lessonService.deleteAvailableTime(created.id(), createAvailableTimeResults.get(0).id());
        entityManager.flush();
        entityManager.clear();

        // then
        LessonDTO lessonDTO = lessonService.readLessonById(created.id()).orElseThrow();
        assertEquals(created.availableTimeDTOList().size() + commands.size() - 1, lessonDTO.availableTimeDTOList().size());
    }

    @Test
    @DisplayName("레슨에서 가능한 시간(availableTime)을 여러개 삭제한다")
    void deleteAvailableTimeAll() {
        // given
        CreateLessonResult created = lessonService.createLesson(createCommand());

        Instant start1 = toInstant(LocalDateTime.of(2025, 1, 10, 14, 0));
        Instant end1 = toInstant(LocalDateTime.of(2025, 1, 10, 16, 0));

        Instant start2 = toInstant(LocalDateTime.of(2025, 1, 17, 14, 0));
        Instant end2 = toInstant(LocalDateTime.of(2025, 1, 17, 16, 0));

        List<CreateAvailableTimeCommand> commands = List.of(
                createAvailableTimeCommand(start1, end1, 1000),
                createAvailableTimeCommand(start2, end2, 2000)
        );

        List<CreateAvailableTimeResult> createAvailableTimeResults = lessonService.addAvailableTime(created.id(), commands);

        // when
        lessonService.deleteAvailableTime(created.id(), createAvailableTimeResults.stream().map(CreateAvailableTimeResult::id).toList());
        entityManager.flush();
        entityManager.clear();

        // then
        LessonDTO result = lessonService.readLessonById(created.id()).orElseThrow();
        assertEquals(created.availableTimeDTOList().size(), result.availableTimeDTOList().size());
    }

    @Test
    @DisplayName("레슨에 옵션(Option)을 추가한다.")
    void addOption() {
        // given
        CreateLessonResult created = lessonService.createLesson(createCommand());
        CreateOptionCommand command = createOptionCommand();

        // when
        lessonService.addOption(created.id(), command);
        entityManager.flush();
        entityManager.clear();

        LessonDTO result = lessonService.readLessonById(created.id()).orElseThrow();

        // then
        assertEquals(created.optionResultList().size() + 1, result.optionDTOList().size());
        assertTrue(
                result.optionDTOList().stream()
                        .anyMatch(op ->
                                op.name().equals(command.name()) &&
                                        op.minute().equals(command.minute()) &&
                                        op.price().equals(command.price())

                        )
        );
    }

    @Test
    @DisplayName("레슨에 옵션(Option)을 여러개 추가한다.")
    void addOptionAll() {
        // given
        CreateLessonResult created = lessonService.createLesson(createCommand());

        CreateOptionCommand command1 = createOptionCommand("option1", 30, 1000);
        CreateOptionCommand command2 = createOptionCommand("option2", 60, 2000);

        List<CreateOptionCommand> commandList = List.of(command1, command2);

        // when
        lessonService.addOption(created.id(), commandList);
        entityManager.flush();
        entityManager.clear();

        LessonDTO result = lessonService.readLessonById(created.id()).orElseThrow();

        // then
        assertEquals(created.optionResultList().size() + 2, result.optionDTOList().size());

        assertTrue(
                result.optionDTOList().stream()
                        .anyMatch(op ->
                                op.name().equals(command1.name()) &&
                                        Objects.equals(op.minute(), command1.minute()) &&
                                        Objects.equals(op.price(), command1.price())
                        )
        );
        assertTrue(
                result.optionDTOList().stream()
                        .anyMatch(op ->
                                op.name().equals(command2.name()) &&
                                        Objects.equals(op.minute(), command2.minute()) &&
                                        Objects.equals(op.price(), command2.price())
                        )
        );

    }

    @Test
    @DisplayName("레슨에서 옵션(Option)을 삭제한다")
    void deleteOption() {
        // given
        CreateLessonResult created = lessonService.createLesson(createCommand());

        CreateOptionCommand command1 = createOptionCommand("option1", 30, 1000);
        CreateOptionCommand command2 = createOptionCommand("option2", 60, 2000);

        List<CreateOptionCommand> commandList = List.of(command1, command2);

        List<CreateOptionResult> createOptionResults = lessonService.addOption(created.id(), commandList);

        // when
        lessonService.deleteOption(created.id(), createOptionResults.get(0).id());
        entityManager.flush();
        entityManager.clear();

        // then
        LessonDTO result = lessonService.readLessonById(created.id()).orElseThrow();
        assertEquals(created.optionResultList().size() + commandList.size() - 1, result.optionDTOList().size());
    }

    @Test
    @DisplayName("레슨에서 옵션(Option)을 여러개 삭제한다")
    void deleteOptionAll() {
        // given
        CreateLessonResult created = lessonService.createLesson(createCommand());

        CreateOptionCommand command1 = createOptionCommand("option1", 30, 1000);
        CreateOptionCommand command2 = createOptionCommand("option2", 60, 2000);

        List<CreateOptionCommand> commandList = List.of(command1, command2);

        List<CreateOptionResult> createOptionResults = lessonService.addOption(created.id(), commandList);

        // when
        lessonService.deleteOption(created.id(), createOptionResults.stream().map(CreateOptionResult::id).toList());
        entityManager.flush();
        entityManager.clear();

        // then
        LessonDTO result = lessonService.readLessonById(created.id()).orElseThrow();
        assertEquals(created.optionResultList().size(), result.optionDTOList().size());
    }

    @Test
    @DisplayName("레슨 제목으로 검색이 가능하다.")
    void searchKeyword() {
        // given
        String keyword = "시니어";
        LessonSearchCondition lessonSearchCondition = new LessonSearchCondition(keyword, null, null, null, LessonSortType.CREATED_AT_DESC, 1, 30);

        // when
        Page<LessonDTO> lessonDTOS = lessonService.searchLesson(lessonSearchCondition);

        // then
        List<Lesson> allByTitleContaining = lessonRepository.findAllByTitleContainingAndDeletedAtIsNull(keyword);
        assertFalse(allByTitleContaining.isEmpty());
        assertEquals(allByTitleContaining.size(), lessonDTOS.getTotalElements());
        assertFalse(
                lessonDTOS.stream().anyMatch(
                        l -> !l.title().contains(keyword)
                )
        );
    }

    @Test
    @DisplayName("레슨 유형으로 검색이 가능하다.")
    void searchLessonType() {
        // given
        LessonType lessonType = LessonType.ONEDAY;
        LessonSearchCondition lessonSearchCondition = new LessonSearchCondition(null, lessonType, null, null, LessonSortType.CREATED_AT_DESC, 1, 30);

        // when
        Page<LessonDTO> lessonDTOS = lessonService.searchLesson(lessonSearchCondition);

        // then
        List<Lesson> allByTitleContaining = lessonRepository.findAllByLessonTypeAndDeletedAtIsNull(lessonType);
        assertFalse(allByTitleContaining.isEmpty());
        assertEquals(allByTitleContaining.size(), lessonDTOS.getTotalElements());
        assertFalse(
                lessonDTOS.stream().anyMatch(
                        l -> !l.lessonType().equals(lessonType)
                )
        );
    }

    @Test
    @DisplayName("카테고리 경로로 검색이 가능하다.")
    void searchCategoryId() {
        // given
        Category parent = categoryRepository.save(
                new Category(null, "개발", null, "9999")
        );

        Category child = categoryRepository.save(
                new Category(null, "백엔드 개발자", parent, "9999:15")
        );

        Category other = categoryRepository.save(
                new Category(null, "디자인", null, "9998")
        );

        lessonService.createLesson(createCommand(parent.getId(), parent.getCategoryPath()));
        lessonService.createLesson(createCommand(child.getId(), child.getCategoryPath()));
        lessonService.createLesson(createCommand(other.getId(), other.getCategoryPath()));
        entityManager.flush(); entityManager.clear();

        LessonSearchCondition condition = new LessonSearchCondition(null, null, "9999", null, null, 0, 5);

        // when
        Page<LessonDTO> result = lessonService.searchLesson(condition);

        // then
        assertThat(result.getTotalElements()).isEqualTo(2);

        assertThat(result.getContent())
                .extracting(LessonDTO::categoryId)
                .containsExactlyInAnyOrder(
                        parent.getId(),
                        child.getId()
                );
    }

    @Test
    @DisplayName("최신순 정렬이 가능하다")
    void searchWithSortByCreatedAtDesc() {
        // given
        LessonSearchCondition lessonSearchCondition = new LessonSearchCondition(null, null, null, null, LessonSortType.CREATED_AT_DESC, 0, 5);

        // when
        Page<LessonDTO> lessonDTOS = lessonService.searchLesson(lessonSearchCondition);

        // then
        List<Lesson> all = lessonRepository.findAllByDeletedAtIsNullOrderByCreatedAtDesc();
        for (int i = 0; i < lessonDTOS.getContent().size(); i++) {
            assertEquals(all.get(i).getCreatedAt(), lessonDTOS.getContent().get(i).createdAt());
        }
    }

    @Test
    @DisplayName("가격 비싼 순 정렬이 가능하다")
    void searchWithSortByPriceDesc() {
        // given
        LessonSearchCondition lessonSearchCondition = new LessonSearchCondition(null, null, null, null, LessonSortType.PRICE_DESC, 0, 5);

        // when
        Page<LessonDTO> lessonDTOS = lessonService.searchLesson(lessonSearchCondition);

        // then
        List<Lesson> all = lessonRepository.findAllByDeletedAtIsNullOrderByPriceDesc();
        for (int i = 0; i < lessonDTOS.getContent().size(); i++) {
            assertEquals(all.get(i).getId(), lessonDTOS.getContent().get(i).id());
        }
    }

    @Test
    @DisplayName("가격 저렴한 순 정렬이 가능하다")
    void searchWithSortByPriceAsc() {
        // given
        LessonSearchCondition lessonSearchCondition = new LessonSearchCondition(null, null, null, null, LessonSortType.PRICE_ASC, 0, 5);

        // when
        Page<LessonDTO> lessonDTOS = lessonService.searchLesson(lessonSearchCondition);

        // then
        List<Lesson> all = lessonRepository.findAllByDeletedAtIsNullOrderByPriceAsc();
        for (int i = 0; i < lessonDTOS.getContent().size(); i++) {
            assertEquals(all.get(i).getId(), lessonDTOS.getContent().get(i).id());
        }
    }

    @Test
    @DisplayName("mentorId로 레슨 검색이 가능하다")
    void searchByMentorId() {
        // given
        String mentorId = "mentor-1";
        String otherMentorId = "mentor-2";

        // mentor-1 레슨
        lessonService.createLesson(createCommand(
                "mentor1 lesson",
                LessonType.ONEDAY,
                "thumb.png",
                "desc",
                "서울",
                mentorId,
                1L,
                Collections.emptyList(),
                Collections.emptyList()
        ));

        // mentor-2 레슨
        lessonService.createLesson(createCommand(
                "mentor2 lesson",
                LessonType.ONEDAY,
                "thumb.png",
                "desc",
                "서울",
                otherMentorId,
                1L,
                Collections.emptyList(),
                Collections.emptyList()
        ));

        entityManager.flush();
        entityManager.clear();

        LessonSearchCondition condition =
                new LessonSearchCondition(
                        null,
                        null,
                        null,
                        mentorId,
                        LessonSortType.CREATED_AT_DESC,
                        0,
                        10
                );

        // when
        Page<LessonDTO> result = lessonService.searchLesson(condition);

        // then
        assertFalse(result.isEmpty());
        assertTrue(
                result.stream().allMatch(
                        lesson -> lesson.mentorId().equals(mentorId)
                )
        );
    }

    @Test
    @DisplayName("mentorId + lessonType 조건으로 검색이 가능하다")
    void searchByMentorIdAndLessonType() {
        // given
        String mentorId = "mentor-1";

        lessonService.createLesson(createCommand(
                "oneday lesson",
                LessonType.ONEDAY,
                "thumb.png",
                "desc",
                "서울",
                mentorId,
                1L,
                Collections.emptyList(),
                Collections.emptyList()
        ));

        lessonService.createLesson(createCommand(
                "mentoring lesson",
                LessonType.MENTORING,
                "thumb.png",
                "desc",
                "서울",
                mentorId,
                1L,
                Collections.emptyList(),
                Collections.emptyList()
        ));

        entityManager.flush();
        entityManager.clear();

        LessonSearchCondition condition =
                new LessonSearchCondition(
                        null,
                        LessonType.ONEDAY,
                        null,
                        mentorId,
                        LessonSortType.CREATED_AT_DESC,
                        0,
                        10
                );

        // when
        Page<LessonDTO> result = lessonService.searchLesson(condition);

        // then
        assertFalse(result.isEmpty());
        assertTrue(
                result.stream().allMatch(
                        lesson ->
                                lesson.mentorId().equals(mentorId) &&
                                        lesson.lessonType() == LessonType.ONEDAY
                )
        );
    }

    @Test
    @DisplayName("mentorId 검색 시 삭제된 레슨은 제외된다")
    void searchByMentorId_excludeDeleted() {
        // given
        String mentorId = "mentor-1";

        CreateLessonResult created =
                lessonService.createLesson(createCommand(
                        "삭제될 레슨",
                        LessonType.ONEDAY,
                        "thumb.png",
                        "desc",
                        "서울",
                        mentorId,
                        1L,
                        Collections.emptyList(),
                        Collections.emptyList()
                ));

        lessonService.deleteLesson(created.id(), mentorId);

        entityManager.flush();
        entityManager.clear();

        LessonSearchCondition condition =
                new LessonSearchCondition(
                        null,
                        null,
                        null,
                        mentorId,
                        LessonSortType.CREATED_AT_DESC,
                        0,
                        10
                );

        // when
        Page<LessonDTO> result = lessonService.searchLesson(condition);

        // then
        assertTrue(result.isEmpty());
    }

    private CreateOptionCommand createOptionCommand() {
        return new CreateOptionCommand(
                "option name",
                30,
                20000
        );
    }

    private CreateOptionCommand createOptionCommand(
            String name,
            Integer minute,
            Integer price) {
        return new CreateOptionCommand(
                name,
                minute,
                price
        );
    }

    private CreateLessonCommand createCommand() {
        return new CreateLessonCommand(
                "title",
                LessonType.ONEDAY,
                "thumbnail.png",
                "테스트 레슨 설명",
                "서울",
                "mentor-1",
                1L,
                "",
                Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(7, ChronoUnit.DAYS),
                null,
                null,
                List.of(createOptionCommand("option1", 30, 1000), createOptionCommand("option2", 60, 2000)),
                List.of(createAvailableTimeCommand())
        );
    }

    private CreateLessonCommand createCommand(Long categoryId, String categoryPath) {
        return new CreateLessonCommand(
                "title",
                LessonType.ONEDAY,
                "thumbnail.png",
                "테스트 레슨 설명",
                "서울",
                "mentor-1",
                categoryId,
                categoryPath,
                Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(7, ChronoUnit.DAYS),
                null,
                null,
                List.of(createOptionCommand("option1", 30, 1000), createOptionCommand("option2", 60, 2000)),
                List.of(createAvailableTimeCommand())
        );
    }

    private CreateAvailableTimeCommand createAvailableTimeCommand() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return new CreateAvailableTimeCommand(
                now,
                now.plus(2, ChronoUnit.HOURS),
                10000,
                1
        );
    }

    private CreateAvailableTimeCommand createAvailableTimeCommand(
            Instant startTime,
            Instant endTime,
            Integer price) {
        return new CreateAvailableTimeCommand(
                startTime,
                endTime,
                price,
                2
        );
    }

    private CreateLessonCommand createCommand(
            String title,
            LessonType lessonType,
            String thumbnailImage,
            String description,
            String location,
            String mentorId,
            Long categoryId,
            List<CreateOptionCommand> optionList,
            List<CreateAvailableTimeCommand> availableTimeList
    ) {
        return new CreateLessonCommand(
                title,
                lessonType,
                thumbnailImage,
                description,
                location,
                mentorId,
                categoryId,
                "",
                Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(1, ChronoUnit.DAYS),
                null,
                3,
                optionList,
                availableTimeList
        );
    }

    private Instant toInstant(LocalDateTime ldt) {
        return ldt.atZone(ZoneId.of("Asia/Seoul")).toInstant();
    }
}