package fr.paug.androidmakers.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.pagerTabIndicatorOffset
import com.google.accompanist.pager.rememberPagerState
import fr.paug.androidmakers.ui.adapter.DaySchedule
import fr.paug.androidmakers.ui.adapter.ScheduleSession
import fr.paug.androidmakers.ui.model.UISession
import fr.paug.androidmakers.util.BookmarksStore
import fr.paug.androidmakers.util.SessionFilter
import fr.paug.androidmakers.util.TimeUtils
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.util.*


@OptIn(ExperimentalPagerApi::class)
@Composable
fun AgendaPager(
    initialPageIndex: Int,
    days: List<String>,
    onSessionClicked: (UISession) -> Unit,
    filterList: List<SessionFilter>
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    val pagerState = rememberPagerState(initialPage = initialPageIndex)

    TabRow(
        // Our selected tab is our current page
        selectedTabIndex = pagerState.currentPage,
        // Override the indicator, using the provided pagerTabIndicatorOffset modifier
        indicator = { tabPositions ->
          TabRowDefaults.Indicator(
              Modifier.pagerTabIndicatorOffset(pagerState, tabPositions)
          )
        }
    ) {
      repeat(days.size) {
        val coroutineScope = rememberCoroutineScope()

        Tab(
            text = { Text(days[it]) },
            selected = pagerState.currentPage == it,
            onClick = {
              coroutineScope.launch {
                pagerState.animateScrollToPage(it)
              }
            },
        )
      }
    }

    HorizontalPager(
        count = days.size,
        state = pagerState,
    ) { page ->
      val viewModel = viewModel<AgendaLayoutViewModel>()
      SwipeRefreshableLceLayout(viewModel = viewModel) {
        val days = agendaToDays(it)
        val items = days[page].roomSchedules.flatMap { it.scheduleSessions }
            .filterSessions(filterList)
            .sorted()
            .map { item ->
              UISession(
                  id = item.sessionId,
                  title = item.title,
                  language = item.language,
                  startDate = OffsetDateTime.parse(item.slot.startDate).toInstant(),
                  endDate = OffsetDateTime.parse(item.slot.endDate).toInstant(),
                  room = getRoomTitle(item, days[page]),
                  roomId = item.roomId,
                  speakers = item.speakers.map {
                    UISession.Speaker(it.name ?: "")
                  },
              )
            }
        if (items.isEmpty()) {
           EmptyLayout()
        } else {
            AgendaColumn(
                sessionsPerStartTime = addSeparators(LocalContext.current, items),
                onSessionClicked = onSessionClicked
            )
        }
      }
    }
  }
}

private fun addSeparators(context: Context, sessions: List<UISession>): Map<String, List<UISession>> {
  return sessions.map {
    TimeUtils.formatShortTime(context, Date(it.startDate.toEpochMilli())) to it
  }
      .groupBy(
          keySelector = { it.first }
      ) {
        it.second
      }
}

// algorithm to filter sessions by applying filters, if the filters is same category we keep
// the combined logic (OR) otherwise it's AND with category filters
// example: Language English && (Rooms Moebius || Rooms A...)
// the algorithm is inspired by Inverted index
// time complexity is O(n * m) where n is the number of sessions and m is the number of filters
private fun List<ScheduleSession>.filterSessions(
    filterList: List<SessionFilter>
): Set<ScheduleSession> {
    val filteredSessions = hashSetOf<ScheduleSession>()
    if (filterList.isEmpty()) {
        filteredSessions.addAll(this)
        return filteredSessions
    }
    val sessionsByFilterType = mutableMapOf<SessionFilter.FilterType, MutableList<ScheduleSession>>()
    for (filter in filterList) {
        if (!sessionsByFilterType.containsKey(filter.type)) {
            sessionsByFilterType[filter.type] = mutableListOf()
        }
    }
    for (session in this) {
        for (filter in filterList) {
            when (filter.type) {
                SessionFilter.FilterType.BOOKMARK -> {
                    if (BookmarksStore.isBookmarked(session.sessionId)) {
                        sessionsByFilterType[filter.type]?.add(session)
                    }
                }
                SessionFilter.FilterType.LANGUAGE -> {
                    if (filter.value == session.language) {
                        sessionsByFilterType[filter.type]?.add(session)
                    }
                }
                SessionFilter.FilterType.ROOM -> {
                    if (filter.value == session.roomId) {
                        sessionsByFilterType[filter.type]?.add(session)
                    }
                }
            }
        }
    }
    //get union join of all ScheduleSessions
    val origin = sessionsByFilterType.values.flatten().toMutableSet()
    sessionsByFilterType.values.forEach { origin.retainAll(it) }
    return origin
}

private fun getRoomTitle(scheduleSession: ScheduleSession, daySchedule: DaySchedule): String {
  var roomTitle = ""
  for (roomSchedule in daySchedule.roomSchedules) {
    if (roomSchedule.roomId == scheduleSession.roomId) {
      roomTitle = roomSchedule.title
    }
  }
  return roomTitle
}