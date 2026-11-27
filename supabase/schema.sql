-- =========================================================
-- Maprog · esquema de la parte social (Supabase / Postgres)
-- Ejecutar entero en el SQL Editor del proyecto.
-- =========================================================

-- Perfiles públicos (uno por usuario de Auth)
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text unique not null check (char_length(username) between 3 and 30),
  bio text default '',
  created_at timestamptz default now()
);

-- Se crea el perfil automáticamente al registrarse
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
declare base text; candidate text; n int := 0;
begin
  base := coalesce(nullif(regexp_replace(lower(new.raw_user_meta_data->>'username'), '[^a-z0-9_.]', '', 'g'), ''),
                   split_part(new.email, '@', 1));
  if char_length(base) < 3 then base := base || '_user'; end if;
  candidate := left(base, 26);
  while exists (select 1 from public.profiles where username = candidate) loop
    n := n + 1; candidate := left(base, 26) || n::text;
  end loop;
  insert into public.profiles (id, username) values (new.id, candidate);
  return new;
end $$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- Rutas
create table if not exists public.trails (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  name text not null check (char_length(name) between 1 and 120),
  description text default '',
  activity text not null default 'hiking',
  difficulty int not null default 2 check (difficulty between 1 and 5),
  distance_m double precision not null default 0,
  elev_gain_m double precision not null default 0,
  elev_loss_m double precision not null default 0,
  max_ele_m double precision not null default 0,
  min_ele_m double precision not null default 0,
  moving_time_s bigint not null default 0,
  total_time_s bigint not null default 0,
  circular boolean not null default false,
  start_lat double precision not null default 0,
  start_lon double precision not null default 0,
  is_public boolean not null default true,
  cover_url text,
  points jsonb not null default '[]'::jsonb,     -- [{lat, lon, ele, time}]
  waypoints jsonb not null default '[]'::jsonb,  -- [{lat, lon, ele, name, type, description, photo}]
  created_at timestamptz default now()
);
create index if not exists trails_start_idx on public.trails (start_lat, start_lon);
create index if not exists trails_user_idx on public.trails (user_id);

create table if not exists public.comments (
  id uuid primary key default gen_random_uuid(),
  trail_id uuid not null references public.trails(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  text text not null check (char_length(text) between 1 and 1000),
  created_at timestamptz default now()
);

-- "Aplausos" (el equivalente a me gusta)
create table if not exists public.claps (
  trail_id uuid references public.trails(id) on delete cascade,
  user_id uuid references public.profiles(id) on delete cascade,
  created_at timestamptz default now(),
  primary key (trail_id, user_id)
);

create table if not exists public.follows (
  follower_id uuid references public.profiles(id) on delete cascade,
  followee_id uuid references public.profiles(id) on delete cascade,
  created_at timestamptz default now(),
  primary key (follower_id, followee_id),
  check (follower_id <> followee_id)
);

-- ---------- Seguridad (Row Level Security) ----------
alter table public.profiles enable row level security;
alter table public.trails   enable row level security;
alter table public.comments enable row level security;
alter table public.claps    enable row level security;
alter table public.follows  enable row level security;

drop policy if exists "perfiles visibles" on public.profiles;
create policy "perfiles visibles" on public.profiles for select using (true);
drop policy if exists "editar mi perfil" on public.profiles;
create policy "editar mi perfil" on public.profiles for update using (auth.uid() = id) with check (auth.uid() = id);

drop policy if exists "rutas publicas o mias" on public.trails;
create policy "rutas publicas o mias" on public.trails for select using (is_public or auth.uid() = user_id);
drop policy if exists "subir mis rutas" on public.trails;
create policy "subir mis rutas" on public.trails for insert with check (auth.uid() = user_id);
drop policy if exists "editar mis rutas" on public.trails;
create policy "editar mis rutas" on public.trails for update using (auth.uid() = user_id);
drop policy if exists "borrar mis rutas" on public.trails;
create policy "borrar mis rutas" on public.trails for delete using (auth.uid() = user_id);

drop policy if exists "comentarios visibles" on public.comments;
create policy "comentarios visibles" on public.comments for select using (true);
drop policy if exists "comentar" on public.comments;
create policy "comentar" on public.comments for insert with check (auth.uid() = user_id);
drop policy if exists "borrar mis comentarios" on public.comments;
create policy "borrar mis comentarios" on public.comments for delete using (auth.uid() = user_id);

drop policy if exists "aplausos visibles" on public.claps;
create policy "aplausos visibles" on public.claps for select using (true);
drop policy if exists "aplaudir" on public.claps;
create policy "aplaudir" on public.claps for insert with check (auth.uid() = user_id);
drop policy if exists "quitar aplauso" on public.claps;
create policy "quitar aplauso" on public.claps for delete using (auth.uid() = user_id);

drop policy if exists "seguidores visibles" on public.follows;
create policy "seguidores visibles" on public.follows for select using (true);
drop policy if exists "seguir" on public.follows;
create policy "seguir" on public.follows for insert with check (auth.uid() = follower_id);
drop policy if exists "dejar de seguir" on public.follows;
create policy "dejar de seguir" on public.follows for delete using (auth.uid() = follower_id);

-- ---------- Vistas para la app ----------
-- Listado ligero de rutas (sin el track) con autor, contadores y TrailRank de Maprog
create or replace view public.trail_feed with (security_invoker = on) as
select t.id, t.user_id, p.username, t.name, t.description, t.activity, t.difficulty,
       t.distance_m, t.elev_gain_m, t.elev_loss_m, t.max_ele_m, t.min_ele_m,
       t.moving_time_s, t.total_time_s, t.circular, t.start_lat, t.start_lon, t.cover_url,
       (select count(*) from jsonb_array_elements(t.waypoints) w where w->>'photo' is not null)::int as photo_count,
       jsonb_array_length(t.waypoints) as waypoint_count,
       (select count(*) from public.claps c where c.trail_id = t.id)::int as claps_count,
       (select count(*) from public.comments m where m.trail_id = t.id)::int as comments_count,
       least(100,
         10
         + case when char_length(t.description) > 300 then 12 when char_length(t.description) > 80 then 8 when char_length(t.description) > 0 then 3 else 0 end
         + least((select count(*) from jsonb_array_elements(t.waypoints) w where w->>'photo' is not null), 5) * 3
         + least(jsonb_array_length(t.waypoints), 5) * 2
         + least((select count(*) from public.claps c where c.trail_id = t.id) * 3, 30)
         + least((select count(*) from public.comments m where m.trail_id = t.id) * 4, 20)
       )::int as trail_rank,
       t.created_at
from public.trails t join public.profiles p on p.id = t.user_id
where t.is_public or t.user_id = auth.uid();

create or replace view public.comment_feed with (security_invoker = on) as
select c.id, c.trail_id, c.user_id, p.username, c.text, c.created_at
from public.comments c join public.profiles p on p.id = c.user_id;

grant select on public.trail_feed, public.comment_feed to anon, authenticated;

-- ---------- Fotos (Storage) ----------
insert into storage.buckets (id, name, public) values ('photos', 'photos', true)
on conflict (id) do nothing;

drop policy if exists "fotos publicas" on storage.objects;
create policy "fotos publicas" on storage.objects for select using (bucket_id = 'photos');
drop policy if exists "subir mis fotos" on storage.objects;
create policy "subir mis fotos" on storage.objects for insert to authenticated
  with check (bucket_id = 'photos' and (storage.foldername(name))[1] = auth.uid()::text);
drop policy if exists "borrar mis fotos" on storage.objects;
create policy "borrar mis fotos" on storage.objects for delete to authenticated
  using (bucket_id = 'photos' and (storage.foldername(name))[1] = auth.uid()::text);
