-- Run once in the HireMyDriver Photos Supabase project's SQL Editor.
-- Firebase third-party integration must trust ONLY hiremydriver-e88f7.
-- No Firebase custom claims, billing upgrade, or service-role key in Android is needed.
begin;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('profile-photos', 'profile-photos', false, 2097152, array['image/jpeg', 'image/png'])
on conflict (id) do update set public = false, file_size_limit = 2097152,
    allowed_mime_types = array['image/jpeg', 'image/png'];

-- Firebase UIDs are strings (not Supabase UUIDs), so use the verified JWT sub claim.
-- Standard Firebase tokens have no Postgres role claim and are assigned the anon DB role.
-- Anon here does NOT grant anonymous access: every request must have a verified Firebase JWT,
-- the exact project issuer/audience, a non-anonymous provider, and its own UID/object name.
create or replace function public.owns_hmd_profile_photo(bucket text, object_name text)
returns boolean language sql stable security invoker set search_path = ''
as $$
    select coalesce(
        bucket = 'profile-photos'
        and (auth.jwt()->>'iss') = 'https://securetoken.google.com/hiremydriver-e88f7'
        and (auth.jwt()->>'aud') = 'hiremydriver-e88f7'
        and (auth.jwt()->>'sub') ~ '^[A-Za-z0-9_-]{1,128}$'
        and (auth.jwt()->'firebase'->>'sign_in_provider') is not null
        and (auth.jwt()->'firebase'->>'sign_in_provider') <> 'anonymous'
        and object_name = (auth.jwt()->>'sub') || '/profile.jpg'
    , false);
$$;
revoke all on function public.owns_hmd_profile_photo(text, text) from public;
grant execute on function public.owns_hmd_profile_photo(text, text) to anon, authenticated;

drop policy if exists "hmd_profile_photo_boundary" on storage.objects;
create policy "hmd_profile_photo_boundary" on storage.objects
as restrictive for all to anon, authenticated
using (bucket_id <> 'profile-photos' or public.owns_hmd_profile_photo(bucket_id, name))
with check (bucket_id <> 'profile-photos' or public.owns_hmd_profile_photo(bucket_id, name));

drop policy if exists "hmd_profile_photo_read" on storage.objects;
create policy "hmd_profile_photo_read" on storage.objects for select to anon, authenticated
using (public.owns_hmd_profile_photo(bucket_id, name));

drop policy if exists "hmd_profile_photo_insert" on storage.objects;
create policy "hmd_profile_photo_insert" on storage.objects for insert to anon, authenticated
with check (public.owns_hmd_profile_photo(bucket_id, name));

drop policy if exists "hmd_profile_photo_update" on storage.objects;
create policy "hmd_profile_photo_update" on storage.objects for update to anon, authenticated
using (public.owns_hmd_profile_photo(bucket_id, name))
with check (public.owns_hmd_profile_photo(bucket_id, name));

commit;
