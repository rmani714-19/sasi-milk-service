import { useEffect, useState } from 'react';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Grid from '@mui/material/Grid2';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import FormControlLabel from '@mui/material/FormControlLabel';
import TextField from '@mui/material/TextField';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { PageHeader } from '../components/PageHeader';
import { FormSection } from '../components/FormSection';
import { CenterBankAccounts } from '../components/CenterBankAccounts';
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
  useEffect(() => {
    if (q.data) setForm(q.data);
  }, [q.data]);

  const save = useMutation({
    mutationFn: async (body: CenterSettings) => (await api.put('/center', body)).data,
    onSuccess: () => {
      toast.success('Centre settings saved');
      qc.invalidateQueries({ queryKey: ['center'] });
    },
    onError: toast.fromError,
  });

  const set = <K extends keyof CenterSettings>(k: K, v: CenterSettings[K]) =>
    setForm((f) => (f ? { ...f, [k]: v } : f));

  return (
    <>
      <PageHeader title="My Center" subtitle="Centre profile, settlement rules and business bank accounts" />
      <QueryBoundary query={q}>
        {() =>
          form && (
            <Stack spacing={3} sx={{ maxWidth: 760 }}>
              <Card>
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

                    <Stack direction="row" justifyContent="flex-end">
                      <Button variant="contained" disabled={save.isPending} onClick={() => form && save.mutate(form)}>
                        {save.isPending ? 'Saving…' : 'Save changes'}
                      </Button>
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>

              <CenterBankAccounts />
            </Stack>
          )
        }
      </QueryBoundary>
    </>
  );
}
