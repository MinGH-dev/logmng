import React, { useState, useEffect, useMemo, useCallback } from 'react';
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import {
  Box,
  Button,
  TextField,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Alert,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  IconButton,
} from '@mui/material';
import DragIndicator from '@mui/icons-material/DragIndicator';
import {
  MENU_TREE,
  PARENT_GROUP_IDS,
  SCREEN_DISPLAY_LABEL_FORM_IDS,
  getDefaultParentGroupIdForScreenId,
  getDefaultScreenLabelForScreenId,
  getDefaultSortOrderForScreenId,
} from '../../constants/menuTree';
import { putScreenDisplayLabels, fetchScreenDisplayLabels } from '../../api/screenDisplayLabelsApi';
import { getErrorMessage } from '../../utils/errorMessage';
import '../UserManagement/UserManagement.css';

const MAX_LEN = 256;

const GROUP_LABEL_BY_ID = Object.fromEntries(MENU_TREE.map((n) => [n.id, n.label]));

/** Section order: MENU_TREE groups, then rows with empty parent (e.g. main). */
const SECTION_GROUP_KEYS = [...PARENT_GROUP_IDS, ''];

/**
 * Reassigns `sortOrder` to 0..n-1 for rows in `groupId` matching `orderedScreenIds` order.
 * `groupId` is `''` for the “기타” bucket (no parent group selected).
 */
export function applySortOrderWithinGroups(rows, groupId, orderedScreenIds) {
  const key = groupId === undefined || groupId === null ? '' : groupId;
  const indexById = new Map(orderedScreenIds.map((id, i) => [id, i]));
  return rows.map((r) => {
    if ((r.parentGroupId ?? '') !== key) return r;
    const idx = indexById.get(r.screenId);
    if (idx === undefined) return r;
    return { ...r, sortOrder: idx };
  });
}

/**
 * Within each `parentGroupId` bucket, renumber `sortOrder` to 0..n-1 by current order + screenId tie-break.
 */
function normalizeSortOrdersWithinGroups(rows) {
  const byGroup = new Map();
  for (const r of rows) {
    const k = r.parentGroupId ?? '';
    if (!byGroup.has(k)) byGroup.set(k, []);
    byGroup.get(k).push(r.screenId);
  }
  const sortOrderByScreenId = new Map();
  for (const [, screenIds] of byGroup) {
    const sorted = [...screenIds].sort((a, b) => {
      const ra = rows.find((x) => x.screenId === a);
      const rb = rows.find((x) => x.screenId === b);
      const d = (ra?.sortOrder ?? 0) - (rb?.sortOrder ?? 0);
      if (d !== 0) return d;
      return a.localeCompare(b);
    });
    sorted.forEach((id, i) => sortOrderByScreenId.set(id, i));
  }
  return rows.map((r) => ({ ...r, sortOrder: sortOrderByScreenId.get(r.screenId) ?? 0 }));
}

function getSortedRowsForGroup(rows, groupKey) {
  return rows
    .filter((r) => (r.parentGroupId ?? '') === groupKey)
    .sort((a, b) => {
      const d = a.sortOrder - b.sortOrder;
      if (d !== 0) return d;
      return a.screenId.localeCompare(b.screenId);
    });
}

function sectionTitle(groupKey) {
  if (groupKey === '') return '기타';
  return GROUP_LABEL_BY_ID[groupKey] ?? groupKey;
}

/**
 * Parse `sortOrder` from API (number or string); missing/invalid → MENU_TREE default for screen.
 */
export function parseSortOrderFromApi(row, screenId) {
  const def = getDefaultSortOrderForScreenId(screenId);
  if (!row || row.sortOrder === undefined || row.sortOrder === null) return def;
  const n = Number(row.sortOrder);
  if (Number.isFinite(n) && n >= 0) return Math.floor(n);
  return def;
}

/**
 * Build rows from GET data + defaults for missing screenIds.
 * When API omits `parentGroupId`, falls back to MENU_TREE default per screen (req 20260407).
 */
export function initialRowsFromLabelItems(items, isSystemAdmin) {
  const byId = new Map();
  (Array.isArray(items) ? items : []).forEach((it) => {
    if (it && typeof it.screenId === 'string') {
      byId.set(it.screenId, it);
    }
  });
  return SCREEN_DISPLAY_LABEL_FORM_IDS.map((screenId) => {
    const row = byId.get(screenId);
    const def = getDefaultScreenLabelForScreenId(screenId);

    let parentGroupId = '';
    if (
      row &&
      typeof row.parentGroupId === 'string' &&
      row.parentGroupId !== '' &&
      PARENT_GROUP_IDS.includes(row.parentGroupId)
    ) {
      parentGroupId = row.parentGroupId;
    } else {
      const treeDefault = getDefaultParentGroupIdForScreenId(screenId);
      if (treeDefault !== undefined && PARENT_GROUP_IDS.includes(treeDefault)) {
        parentGroupId = treeDefault;
      }
    }

    const sortOrder = parseSortOrderFromApi(row, screenId);

    return {
      screenId,
      labelUser: row?.labelUser ?? def,
      labelAdmin: isSystemAdmin ? (row?.labelAdmin ?? '') : '',
      parentGroupId,
      sortOrder,
    };
  });
}

function SortableScreenRow({ row, onFieldChange, onParentChange }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: row.screenId,
  });
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.65 : 1,
  };

  return (
    <TableRow ref={setNodeRef} style={style}>
      <TableCell sx={{ width: 48, py: 0.5 }}>
        <IconButton
          size="small"
          aria-label={`${row.screenId} 순서 변경 (드래그)`}
          {...attributes}
          {...listeners}
          sx={{ cursor: 'grab' }}
        >
          <DragIndicator fontSize="small" aria-hidden />
        </IconButton>
      </TableCell>
      <TableCell sx={{ fontFamily: 'monospace', whiteSpace: 'nowrap' }}>{row.screenId}</TableCell>
      <TableCell>
        <TextField
          value={row.labelUser}
          onChange={(e) => onFieldChange(row.screenId, 'labelUser', e.target.value)}
          fullWidth
          size="small"
          required
          inputProps={{ maxLength: MAX_LEN, 'aria-label': `${row.screenId} 사용자 표시 이름` }}
        />
      </TableCell>
      <TableCell sx={{ minWidth: 200 }}>
        <FormControl fullWidth size="small">
          <InputLabel id={`parent-label-${row.screenId}`}>상위 메뉴</InputLabel>
          <Select
            labelId={`parent-label-${row.screenId}`}
            label="상위 메뉴"
            value={row.parentGroupId}
            onChange={(e) => onParentChange(row.screenId, e.target.value)}
            inputProps={{ 'aria-label': `${row.screenId} 상위 메뉴` }}
          >
            <MenuItem value="">
              <em>기본 (MENU_TREE)</em>
            </MenuItem>
            {PARENT_GROUP_IDS.map((id) => (
              <MenuItem key={id} value={id}>
                {GROUP_LABEL_BY_ID[id] ?? id}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      </TableCell>
      <TableCell>
        <TextField
          value={row.labelAdmin}
          onChange={(e) => onFieldChange(row.screenId, 'labelAdmin', e.target.value)}
          fullWidth
          size="small"
          placeholder="내부 참고용"
          inputProps={{ maxLength: MAX_LEN, 'aria-label': `${row.screenId} 관리자 메모` }}
        />
      </TableCell>
    </TableRow>
  );
}

function GroupDragSection({
  groupKey,
  groupRows,
  setRows,
  onFieldChange,
  onParentChange,
}) {
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  const ids = useMemo(() => groupRows.map((r) => r.screenId), [groupRows]);

  const handleDragEnd = useCallback(
    (event) => {
      const { active, over } = event;
      if (!over || active.id === over.id) return;
      const oldIndex = ids.indexOf(active.id);
      const newIndex = ids.indexOf(over.id);
      if (oldIndex < 0 || newIndex < 0) return;
      const orderedScreenIds = arrayMove(ids, oldIndex, newIndex);
      setRows((prev) => applySortOrderWithinGroups(prev, groupKey, orderedScreenIds));
    },
    [ids, groupKey, setRows]
  );

  return (
    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
      <SortableContext items={ids} strategy={verticalListSortingStrategy}>
        <TableBody>
          {groupRows.map((row) => (
            <SortableScreenRow
              key={row.screenId}
              row={row}
              onFieldChange={onFieldChange}
              onParentChange={onParentChange}
            />
          ))}
        </TableBody>
      </SortableContext>
    </DndContext>
  );
}

const ScreenDisplayLabelsSettings = ({ user, labelItems, onLabelsUpdated }) => {
  const isSystemAdmin = user?.isSystemAdmin === true;
  const [rows, setRows] = useState(() => initialRowsFromLabelItems(labelItems, isSystemAdmin));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  useEffect(() => {
    setRows(initialRowsFromLabelItems(labelItems, isSystemAdmin));
  }, [labelItems, isSystemAdmin]);

  const canSave = isSystemAdmin;

  const handleChange = useCallback((screenId, field, value) => {
    setRows((prev) => prev.map((r) => (r.screenId === screenId ? { ...r, [field]: value } : r)));
  }, []);

  const handleParentChange = useCallback((screenId, newParent) => {
    setRows((prev) => {
      const key = newParent ?? '';
      const next = prev.map((r) =>
        r.screenId === screenId ? { ...r, parentGroupId: newParent } : { ...r }
      );
      const siblings = next.filter((r) => r.screenId !== screenId && (r.parentGroupId ?? '') === key);
      const maxSo = siblings.length === 0 ? -1 : Math.max(...siblings.map((r) => r.sortOrder));
      const withMoved = next.map((r) =>
        r.screenId === screenId ? { ...r, sortOrder: maxSo + 1 } : r
      );
      return normalizeSortOrdersWithinGroups(withMoved);
    });
  }, []);

  const payload = useMemo(
    () =>
      rows.map((r) => {
        const item = {
          screenId: r.screenId,
          labelUser: typeof r.labelUser === 'string' ? r.labelUser.trim() : '',
        };
        const adminTrim = typeof r.labelAdmin === 'string' ? r.labelAdmin.trim() : '';
        if (isSystemAdmin && adminTrim !== '') {
          item.labelAdmin = adminTrim;
        }
        const so = Number(r.sortOrder);
        item.sortOrder =
          Number.isFinite(so) && so >= 0 ? Math.floor(so) : getDefaultSortOrderForScreenId(r.screenId);
        if (
          typeof r.parentGroupId === 'string' &&
          r.parentGroupId !== '' &&
          PARENT_GROUP_IDS.includes(r.parentGroupId)
        ) {
          item.parentGroupId = r.parentGroupId;
        }
        return item;
      }),
    [rows, isSystemAdmin]
  );

  const handleSave = async () => {
    if (!canSave) return;
    setSaving(true);
    setError(null);
    setSuccess(null);
    try {
      await putScreenDisplayLabels(payload);
      setSuccess('저장되었습니다.');
      const fresh = await fetchScreenDisplayLabels();
      if (typeof onLabelsUpdated === 'function') {
        onLabelsUpdated(fresh);
      }
    } catch (e) {
      const status = e?.status;
      if (status === 401) {
        setError('로그인이 필요합니다. 다시 로그인한 뒤 시도하세요.');
      } else if (status === 403) {
        setError('시스템 관리자만 저장할 수 있습니다.');
      } else if (status === 400) {
        setError(getErrorMessage(e, '입력값을 확인하세요. 화면 ID·글자 수 제한을 확인합니다.'));
      } else if (status === 404) {
        setError('화면 표시 이름 API를 찾을 수 없습니다. API 기본 주소 설정을 확인하세요.');
      } else {
        setError(getErrorMessage(e, '저장에 실패했습니다.'));
      }
    } finally {
      setSaving(false);
    }
  };

  if (!isSystemAdmin) {
    return (
      <Box className="user-management" sx={{ p: 0 }}>
        <Typography variant="h2" component="h2" sx={{ fontSize: '1.25rem', mb: 1 }}>
          화면 표시 이름
        </Typography>
        <p className="user-management-forbidden">시스템 관리자만 접근할 수 있습니다.</p>
      </Box>
    );
  }

  const colSpan = 5;

  return (
    <Box className="user-management" sx={{ p: 0, maxWidth: 960 }}>
      <Typography variant="h2" component="h2" sx={{ fontSize: '1.25rem', mb: 1 }}>
        화면 표시 이름
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        사이드바 및 로그 화면 제목에 보이는 이름을 설정합니다. 화면 ID(기술 식별자)는 변경되지 않습니다.
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        같은 상위 메뉴 안에서는 드래그 핸들로 순서를 바꿀 수 있습니다. 순서는 해당 그룹 안에서만 적용됩니다.
      </Typography>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }} role="alert">
          {error}
        </Alert>
      )}
      {success && (
        <Alert severity="success" sx={{ mb: 2 }} onClose={() => setSuccess(null)}>
          {success}
        </Alert>
      )}
      <TableContainer component={Paper} variant="outlined" sx={{ mb: 2 }}>
        <Table size="small" aria-label="화면 표시 이름 설정">
          <TableHead>
            <TableRow>
              <TableCell scope="col" sx={{ width: 48 }}>
                드래그로 순서
              </TableCell>
              <TableCell scope="col">화면 ID</TableCell>
              <TableCell scope="col">사용자에게 보이는 이름</TableCell>
              <TableCell scope="col">상위 메뉴</TableCell>
              <TableCell scope="col">관리자 메모 (선택)</TableCell>
            </TableRow>
          </TableHead>
          {SECTION_GROUP_KEYS.map((groupKey) => {
            const groupRows = getSortedRowsForGroup(rows, groupKey);
            if (groupRows.length === 0) return null;
            return (
              <React.Fragment key={groupKey === '' ? '__other__' : groupKey}>
                <TableBody>
                  <TableRow>
                    <TableCell
                      colSpan={colSpan}
                      component="th"
                      scope="colgroup"
                      sx={{
                        bgcolor: 'action.hover',
                        py: 1,
                        fontWeight: 600,
                        borderBottom: (theme) => `1px solid ${theme.palette.divider}`,
                      }}
                    >
                      {sectionTitle(groupKey)}
                    </TableCell>
                  </TableRow>
                </TableBody>
                <GroupDragSection
                  groupKey={groupKey}
                  groupRows={groupRows}
                  setRows={setRows}
                  onFieldChange={handleChange}
                  onParentChange={handleParentChange}
                />
              </React.Fragment>
            );
          })}
        </Table>
      </TableContainer>
      <Button variant="contained" onClick={handleSave} disabled={saving}>
        {saving ? '저장 중…' : '저장'}
      </Button>
    </Box>
  );
};

export default ScreenDisplayLabelsSettings;
