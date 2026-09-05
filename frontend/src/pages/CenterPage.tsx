import { useEffect, useState } from 'react';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Grid from '@mui/material/Grid2';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import FormControlLabel from '@mui/material/FormControlLabel';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import * as V from '../lib/validate';
import { PageHeader } from '../components/PageHeader';
import { FormSection } from '../components/FormSection';
import { QueryBoundary, useToast } from '../components/ui';
import type { CenterSettings } from '../api/types';

const ROUNDING = ['HALF_EVEN', 'HALF_UP', 'HALF_DOWN', 'UP', 'DOWN', 'CEILING', 'FLOOR', 'NONE'];

export function CenterPage() {
  const toast = useToast();
  const qc = useQueryClient();
  const q = useQuery({
    queryKey: ['center'],
    queryFn: async () => (await api.get<CenterSettings>('/center')).data,
  });
  const [form, setForm] = useState<CenterSettings | null>(null);
  const [payoutAccountNumber, setPayoutAccountNumber] = useState('');
  const [tried, setTried] = useState(false);
  useEffect(() => {
    if (q.data) setForm(q.data);
  }, [q.data]);

  const save = useMutation({
    mutationFn: async (body: CenterSettings & { payoutAccountNumber?: string }) =>
      (await api.put('/center', body)).data,
    onSuccess: () => {
      toast.success('Centre settings saved');
      setPayoutAccountNumber('');
      setTried(false);
      qc.invalidateQueries({ queryKey: ['center'] });
    },
    onError: toast.fromError,
  });

  const set = <K extends keyof CenterSettings>(k: K, v: CenterSettings[K]) =>
    setForm((f) => (f ? { ...f, [k]: v } : f));

  const keepAccount = !!form?.payoutAccountNumberMasked;
  const touchingPayout =
    !!form?.payoutAccountHolderName || !!form?.payoutIfsc || !!form?.payoutBankName || !!payoutAccountNumber || keepAccount;
  const payoutErrors: Record<string, string> = {
    payoutAccountHolderName: touchingPayout ? V.required('Account holder name')(form?.payoutAccountHolderName ?? '') : '',
    payoutAccountNumber: touchingPayout ? V.accountNumber(payoutAccountNumber, keepAccount) : '',
    payoutIfsc: touchingPayout ? V.ifsc(form?.payoutIfsc ?? '') : '',
    payoutBankName: touchingPayout ? V.required('Bank name')(form?.payoutBankName ?? '') : '',
  };
  const showErr = (k: string) => (tried ? payoutErrors[k] ?? '' : '');
  const valid = V.allValid(payoutErrors);

  const submit = () => {
    setTried(true);
    if (!form || !valid) return;
    save.mutate({ ...form, payoutAccountNumber: payoutAccountNumber.trim() || undefined });
  };

  return (
    <>
      <PageHeader title="My Center" subtitle="Centre profile, settlement rules and the business payout account" />
      <QueryBoundary query={q}>
        {() =>
          form && (
            <Card sx={{ maxWidth: 760 }}>
              <CardContent>
                <Stack spacing={3}>
                  <FormSection title="Centre details">
                    <Grid container spacing={2}>
                      <Grid size={{ xs: 12, sm: 6 }}>
                        <TextField label="Centre name" value={form.centerName} onChange={(e) => set('centerName', e.target.value)} />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6 }}>
                        <TextField label="Owner name" value={form.ownerName ?? ''} onChange={(e) => set('ownerName', e.target.value)} />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6 }}>
                        <TextField label="Mobile" value={form.mobileNumber ?? ''} onChange={(e) => set('mobileNumber', e.target.value)} />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6 }}>
                        <TextField label="Email" value={form.email ?? ''} onChange={(e) => set('email', e.target.value)} />
                      </Grid>
                      <Grid size={12}>
                        <TextField label="Address" value={form.address ?? ''} onChange={(e) => set('address', e.target.value)} multiline minRows={2} />
                      </Grid>
                    </Grid>
                  </FormSection>

                  <FormSection
                    title="Business payout account"
                    hint="Configure this once — every weekly/monthly farmer payment and driver salary run pre-fills it during bank-balance verification instead of asking again."
                  >
                    <Grid container spacing={2}>
                      <Grid size={12}>
                        <TextField
                          label="Account Holder Name"
                          value={form.payoutAccountHolderName ?? ''}
                          onChange={(e) => set('payoutAccountHolderName', e.target.value)}
                          error={!!showErr('payoutAccountHolderName')}
                          helperText={showErr('payoutAccountHolderName')}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6 }}>
                        <TextField
                          label="Account Number"
                          value={payoutAccountNumber}
                          onChange={(e) => setPayoutAccountNumber(V.onlyDigits(e.target.value).slice(0, 18))}
                          error={!!showErr('payoutAccountNumber')}
                          helperText={
                            showErr('payoutAccountNumber') ||
                            (keepAccount
                              ? `Current: ${form.payoutAccountNumberMasked} — leave blank to keep`
                              : 'Digits only, 9–18 long')
                          }
                          inputProps={{ inputMode: 'numeric', maxLength: 18 }}
                        />
                      </Grid>
                      <Grid size={{ xs: 12, sm: 6 }}>
                        <TextField
                          label="IFSC Code"
                          value={form.payoutIfsc ?? ''}
                          onChange={(e) => set('payoutIfsc', V.upper(e.target.value).replace(/[^A-Z0-9]/g, '').slice(0, 11))}
                          error={!!showErr('payoutIfsc')}
                          helperText={showErr('payoutIfsc')}
                          inputProps={{ maxLength: 11, style: { textTransform: 'uppercase' } }}
                        />
                      </Grid>
                      <Grid size={12}>
                        <TextField
                          label="Bank Name"
                          value={form.payoutBankName ?? ''}
                          onChange={(e) => set('payoutBankName', e.target.value)}
                          error={!!showErr('payoutBankName')}
                          helperText={showErr('payoutBankName')}
                        />
                      </Grid>
                    </Grid>
                    {form.payoutAccountComplete && form.payoutAccountUpdatedAt && (
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                        Last updated {new Date(form.payoutAccountUpdatedAt).toLocaleString()}
                      </Typography>
                    )}
                    {!form.payoutAccountComplete && (
                      <Alert severity="warning" sx={{ mt: 2 }}>
                        No payout account configured yet — bank-balance verification will ask for the account details
                        manually on every payment run until this is filled in.
                      </Alert>
                    )}
                  </FormSection>

                  <FormSection title="Settlement rules">
                    <Grid container spacing={2}>
                      <Grid size={{ xs: 6, sm: 4 }}>
                        <TextField select label="Rounding mode" value={form.roundingMode} onChange={(e) => set('roundingMode', e.target.value)}>
                          {ROUNDING.map((m) => (
                            <MenuItem key={m} value={m}>
                              {m}
                            </MenuItem>
                          ))}
                        </TextField>
                      </Grid>
                      <Grid size={{ xs: 6, sm: 4 }}>
                        <TextField
                          label="Rounding scale"
                          type="number"
                          value={form.roundingScale}
                          onChange={(e) => set('roundingScale', Number(e.target.value))}
                        />
                      </Grid>
                      <Grid size={{ xs: 6, sm: 4 }}>
                        <TextField select label="Incentive stacking" value={form.incentiveStacking} onChange={(e) => set('incentiveStacking', e.target.value)}>
                          <MenuItem value="ADD">ADD</MenuItem>
                          <MenuItem value="REPLACE">REPLACE</MenuItem>
                        </TextField>
                      </Grid>
                      <Grid size={{ xs: 6, sm: 4 }}>
                        <TextField
                          label="Incentive max / litre"
                          type="number"
                          value={form.incentiveMaxPerLitre}
                          onChange={(e) => set('incentiveMaxPerLitre', Number(e.target.value))}
                        />
                      </Grid>
                      <Grid size={{ xs: 6, sm: 4 }}>
                        <TextField
                          label="Settlement day (1=Mon)"
                          type="number"
                          value={form.settlementDayOfWeek}
                          onChange={(e) => set('settlementDayOfWeek', Number(e.target.value))}
                        />
                      </Grid>
                      <Grid size={12}>
                        <FormControlLabel
                          control={<Switch checked={form.allowMissingRate} onChange={(e) => set('allowMissingRate', e.target.checked)} />}
                          label="Allow collection when no rate is configured"
                        />
                      </Grid>
                    </Grid>
                  </FormSection>

                  {tried && !valid && (
                    <Typography variant="caption" color="error">
                      Please fix the highlighted fields in the payout account section.
                    </Typography>
                  )}

                  <Stack direction="row" justifyContent="flex-end">
                    <Button variant="contained" disabled={save.isPending} onClick={submit}>
                      {save.isPending ? 'Saving…' : 'Save changes'}
                    </Button>
                  </Stack>
                </Stack>
              </CardContent>
            </Card>
          )
        }
      </QueryBoundary>
    </>
  );
}
