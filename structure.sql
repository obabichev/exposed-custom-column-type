-- https://www.postgresql.org/docs/current/datatype-enum.html

create type mood as enum ('sad', 'ok', 'happy');

create table person
(
    name text,
    mood mood
);
