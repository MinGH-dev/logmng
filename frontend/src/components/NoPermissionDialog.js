import React from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Typography,
} from '@mui/material';

/** Exact copy per req 20260407-external-dept-employee-ad-login */
export const NO_PERMISSION_MESSAGE_KO =
  '접근 권한이 없습니다. 사용을 위해 보안담당자에게 권한을 요청하시기 바랍니다.';

/**
 * Blocking modal for authenticated users with zero screen permissions.
 * zIndex: theme.zIndex.modal (default 1300). Primary action only; Escape/backdrop do not dismiss.
 */
const NoPermissionDialog = ({ open, onConfirm, idPrefix = 'no-permission-dialog' }) => {
  const titleId = `${idPrefix}-title`;
  const descId = `${idPrefix}-desc`;

  return (
    <Dialog
      open={open}
      onClose={(event, reason) => {
        if (reason === 'backdropClick' || reason === 'escapeKeyDown') {
          return;
        }
      }}
      disableEscapeKeyDown
      aria-labelledby={titleId}
      aria-describedby={descId}
    >
      <DialogTitle id={titleId}>알림</DialogTitle>
      <DialogContent>
        <Typography id={descId} component="p" variant="body1">
          {NO_PERMISSION_MESSAGE_KO}
        </Typography>
      </DialogContent>
      <DialogActions>
        <Button
          type="button"
          variant="contained"
          color="primary"
          onClick={onConfirm}
          autoFocus
        >
          확인
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default NoPermissionDialog;
